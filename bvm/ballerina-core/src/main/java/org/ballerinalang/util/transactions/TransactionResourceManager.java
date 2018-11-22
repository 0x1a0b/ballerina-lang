/*
 *  Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.ballerinalang.util.transactions;

import org.ballerinalang.bre.bvm.WorkerExecutionContext;
import org.ballerinalang.model.values.BFunctionPointer;
import org.ballerinalang.model.values.BString;
import org.ballerinalang.model.values.BValue;
import org.ballerinalang.util.exceptions.BallerinaException;
import org.ballerinalang.util.program.BLangFunctions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import static javax.transaction.xa.XAResource.TMNOFLAGS;
import static javax.transaction.xa.XAResource.TMSUCCESS;

/**
 * {@code TransactionResourceManager} registry for transaction contexts.
 *
 * @since 0.964.0
 */
public class TransactionResourceManager {

    private static TransactionResourceManager transactionResourceManager = null;
    private static final Logger log = LoggerFactory.getLogger(TransactionResourceManager.class);
    private Map<String, List<BallerinaTransactionContext>> resourceRegistry;
    private Map<String, Xid> xidRegistry;

    private Map<Integer, BFunctionPointer> committedFuncRegistry;
    private Map<Integer, BFunctionPointer> abortedFuncRegistry;

    private ParticipantRegistry participantRegistry;

    private TransactionResourceManager() {
        resourceRegistry = new HashMap<>();
        xidRegistry = new HashMap<>();
        committedFuncRegistry = new HashMap<>();
        abortedFuncRegistry = new HashMap<>();
        participantRegistry = new ParticipantRegistry();
    }

    public static TransactionResourceManager getInstance() {
        if (transactionResourceManager == null) {
            synchronized (TransactionResourceManager.class) {
                if (transactionResourceManager == null) {
                    transactionResourceManager = new TransactionResourceManager();
                }
            }
        }
        return transactionResourceManager;
    }

    /**
     * This method will register connection resources with a particular transaction.
     *
     * @param transactionId      the global transaction id
     * @param transactionBlockId the block id of the transaction
     * @param txContext          ballerina transaction context which includes the underlying connection info
     */
    public void register(String transactionId, int transactionBlockId, BallerinaTransactionContext txContext) {
        String combinedId = generateCombinedTransactionId(transactionId, transactionBlockId);
        resourceRegistry.computeIfAbsent(combinedId, resourceList -> new ArrayList<>()).add(txContext);
    }

    /**
     * This method will register a committed function handler of a particular transaction.
     *
     * @param transactionBlockId the block id of the transaction
     * @param bFunctionPointer   the function pointer for the committed function
     */
    public void registerCommittedFunction(int transactionBlockId, BFunctionPointer bFunctionPointer) {
        committedFuncRegistry.put(transactionBlockId, bFunctionPointer);
    }

    /**
     * This method will register an aborted function handler of a particular transaction.
     *
     * @param transactionBlockId the block id of the transaction
     * @param bFunctionPointer   the function pointer for the aborted function
     */
    public void registerAbortedFunction(int transactionBlockId, BFunctionPointer bFunctionPointer) {
        abortedFuncRegistry.put(transactionBlockId, bFunctionPointer);
    }

    /**
     * Register a participation in a global transaction.
     *
     * @param gTransactionId         global transaction id
     * @param committed              function pointer to invoke when this transaction committed
     * @param aborted                function pointer to invoke when this transaction aborted
     * @param workerExecutionContext
     *
     * @since 0.985.0
     */
    public void registerParticipation(String gTransactionId, BFunctionPointer committed, BFunctionPointer aborted,
                                      WorkerExecutionContext workerExecutionContext) {
        this.participantRegistry.register(gTransactionId, committed, aborted);

        int transactionBlockId = -1; // This participant is registering without a transaction statement.

        LocalTransactionInfo localTransactionInfo = workerExecutionContext.getLocalTransactionInfo();
        BValue[] bValues = TransactionUtils.notifyTransactionBegin(workerExecutionContext,
                localTransactionInfo.getGlobalTransactionId(),
                localTransactionInfo.getURL(), transactionBlockId, localTransactionInfo.getProtocol());
        log.info("participant registered: " + bValues[0]);
    }

    /**
     * Register a participant in the local transaction. This is intended for participant function registration.
     *
     * @param gTransactionId  global transaction id
     * @param participantName name of the participant to uniquely identify it
     * @param committed       function pointer to invoke when this transaction committed
     * @param aborted         function pointer to invoke when this transaction aborted
     *
     * @since 0.985.0
     */
    public void registerLocalParticipant(String gTransactionId, String participantName, BFunctionPointer committed,
                                         BFunctionPointer aborted) {
        participantRegistry.register(gTransactionId, participantName, committed, aborted);

    }

    /**
     * This method acts as the callback which notify all the resources participated in the given transaction. For local
     * transaction scenarios, this phase will be ignored.
     *
     * @param transactionId      the global transaction id
     * @param transactionBlockId the block id of the transaction
     * @return the status of the prepare operation
     */
    public boolean prepare(String transactionId, int transactionBlockId) {
        String combinedId = generateCombinedTransactionId(transactionId, transactionBlockId);
        List<BallerinaTransactionContext> txContextList = resourceRegistry.get(combinedId);
        if (txContextList != null) {
            for (BallerinaTransactionContext ctx : txContextList) {
                try {
                    XAResource xaResource = ctx.getXAResource();
                    if (xaResource != null) {
                        Xid xid = xidRegistry.get(combinedId);
                        xaResource.prepare(xid);
                    }
                } catch (Throwable e) {
                    log.error("error in prepare the transaction, " + combinedId + ":" + e.getMessage(), e);
                    return false;
                }
            }
        }

        if (participantRegistry.hasParticipants(transactionId)) {
            boolean status = participantRegistry.prepareCommit(transactionId);
            log.info("Transaction prepare (participants): " + (status ? "success" : "failed"));
            return status;
        }
        log.info("Transaction prepare: success)");
        return true;
    }

    /**
     * This method acts as the callback which commits all the resources participated in the given transaction.
     *
     * @param transactionId      the global transaction id
     * @param transactionBlockId the block id of the transaction
     * @return the status of the commit operation
     */
    public boolean notifyCommit(String transactionId, int transactionBlockId) {
        String combinedId = generateCombinedTransactionId(transactionId, transactionBlockId);
        boolean commitSuccess = true;
        List<BallerinaTransactionContext> txContextList = resourceRegistry.get(combinedId);
        if (txContextList != null) {
            for (BallerinaTransactionContext ctx : txContextList) {
                try {
                    XAResource xaResource = ctx.getXAResource();
                    if (xaResource != null) {
                        Xid xid = xidRegistry.get(combinedId);
                        xaResource.commit(xid, false);
                    } else {
                        ctx.commit();
                    }
                } catch (Throwable e) {
                    log.error("error in commit the transaction, " + combinedId + ":" + e.getMessage(), e);
                    commitSuccess = false;
                } finally {
                    ctx.close();
                }
            }
        }
        // TODO: 10/31/18 remove; there is nor commited function just a committed block
        invokeCommittedFunction(transactionId, transactionBlockId);
        removeContextsFromRegistry(combinedId, transactionId);
        return commitSuccess;
    }

    /**
     * This method acts as the callback which aborts all the resources participated in the given transaction.
     *
     * @param transactionId      the global transaction id
     * @param transactionBlockId the block id of the transaction
     * @param isRetryAttempt     whether this is a retry attempt
     * @return the status of the abort operation
     */
    public boolean notifyAbort(String transactionId, int transactionBlockId, boolean isRetryAttempt) {
        String combinedId = generateCombinedTransactionId(transactionId, transactionBlockId);
        boolean abortSuccess = true;
        List<BallerinaTransactionContext> txContextList = resourceRegistry.get(combinedId);
        if (txContextList != null) {
            for (BallerinaTransactionContext ctx : txContextList) {
                try {
                    XAResource xaResource = ctx.getXAResource();
                    Xid xid = xidRegistry.get(combinedId);
                    if (xaResource != null) {
                        ctx.getXAResource().rollback(xid);
                    } else {
                        ctx.rollback();
                    }
                } catch (Throwable e) {
                    log.error("error in abort the transaction, " + combinedId + ":" + e.getMessage(), e);
                    abortSuccess = false;
                } finally {
                    ctx.close();
                }
            }
        }
        //For the retry  attempt failures the aborted function should not be invoked. It should invoked only when the
        //whole transaction aborts after all the retry attempts.
        if (!isRetryAttempt) {
            invokeAbortedFunction(transactionId, transactionBlockId);
        }
        removeContextsFromRegistry(combinedId, transactionId);
        return abortSuccess;
    }

    /**
     * This method starts a transaction for the given xa resource. If there is no transaction is started for the
     * given XID a new transaction is created.
     *
     * @param transactionId      the global transaction id
     * @param transactionBlockId the block id of the transaction
     * @param xaResource         the XA resource which participates in the transaction
     */
    public void beginXATransaction(String transactionId, int transactionBlockId, XAResource xaResource) {
        String combinedId = generateCombinedTransactionId(transactionId, transactionBlockId);
        Xid xid = xidRegistry.get(combinedId);
        if (xid == null) {
            xid = XIDGenerator.createXID();
            xidRegistry.put(combinedId, xid);
        }
        try {
            xaResource.start(xid, TMNOFLAGS);
        } catch (XAException e) {
            throw new BallerinaException("error in starting the XA transaction: id: " + combinedId + " error:" + e
                    .getMessage());
        }
    }

    /**
     * This method marks the end of a transaction for the given transaction id.
     *
     * @param transactionId      the global transaction id
     * @param transactionBlockId the block id of the transaction
     */
    void endXATransaction(String transactionId, int transactionBlockId) {
        String combinedId = generateCombinedTransactionId(transactionId, transactionBlockId);
        Xid xid = xidRegistry.get(combinedId);
        if (xid != null) {
            List<BallerinaTransactionContext> txContextList = resourceRegistry.get(combinedId);
            if (txContextList != null) {
                for (BallerinaTransactionContext ctx : txContextList) {
                    try {
                        XAResource xaResource = ctx.getXAResource();
                        if (xaResource != null) {
                            ctx.getXAResource().end(xid, TMSUCCESS);
                        }
                    } catch (Throwable e) {
                        throw new BallerinaException(
                                "error in ending the XA transaction: id: " + combinedId + " error:" + e.getMessage());
                    }
                }
            }
        }
    }

    void rollbackTransaction(String transactionId, int transactionBlockId) {
        endXATransaction(transactionId, transactionBlockId);
        notifyAbort(transactionId, transactionBlockId, true);
        participantRegistry.participantFailed(transactionId);
    }

    private void removeContextsFromRegistry(String transactionCombinedId, String gTransactionId) {
        resourceRegistry.remove(transactionCombinedId);
        xidRegistry.remove(transactionCombinedId);
        participantRegistry.remove(gTransactionId);
    }

    private String generateCombinedTransactionId(String transactionId, int transactionBlockId) {
        return transactionId + ":" + transactionBlockId;
    }

    private void invokeCommittedFunction(String transactionId, int transactionBlockId) {
        BFunctionPointer fp = committedFuncRegistry.get(transactionBlockId);
        BValue[] args = {new BString(transactionId + ":" + transactionBlockId)};
        if (fp != null) {
            BLangFunctions.invokeCallable(fp.value(), args);
        }
        List<BFunctionPointer> funcs = participantRegistry.getCommittedFuncs(transactionId);
        invokeFunctions(args, funcs);
        participantRegistry.purge(transactionId);

    }

    private void invokeAbortedFunction(String transactionId, int transactionBlockId) {
        BFunctionPointer fp = abortedFuncRegistry.get(transactionBlockId);
        BValue[] args = {new BString(transactionId + ":" + transactionBlockId)};
        if (fp != null) {
            BLangFunctions.invokeCallable(fp.value(), args);
        }
        List<BFunctionPointer> funcs = participantRegistry.getAbortedFuncs(transactionId);
        invokeFunctions(args, funcs);
        participantRegistry.purge(transactionId);
    }

    private void invokeFunctions(BValue[] args, List<BFunctionPointer> funcs) {
        if (funcs != null) {
            for (BFunctionPointer func : funcs) {
                if (func != null) {
                    BLangFunctions.invokeCallable(func.value(), args);
                }
            }
        }
    }

    public void notifySuccess(String transactionId) {
        // let the transaction know that the corresponding service.resource finished successfully.
    }

    public void notifyFailure(String gTransactionId) {
        participantRegistry.participantFailed(gTransactionId);
        // the resource excepted (uncaught)
        log.info("Callable unit excepted corresponding to global trx id : " + gTransactionId);
    }

    public void notifyFailure(String gTransactionId, String uniqueName) {
        participantRegistry.participantFailed(gTransactionId, uniqueName);
    }

}
