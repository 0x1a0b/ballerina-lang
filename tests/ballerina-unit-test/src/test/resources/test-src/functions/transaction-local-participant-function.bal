// Copyright (c) 2018 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
//
// WSO2 Inc. licenses this file to you under the Apache License,
// Version 2.0 (the "License"); you may not use this file except
// in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

import ballerina/io;
import ballerina/log;
import ballerina/transactions;

string S = "";


@transactions:Participant {
    oncommitFunc:commitFunc,
    onabortFunc:abortFunc
}
public function participantFoo() {
    S = S + " in-participantFoo";
    io:println("Hello, World!");
}

public function commitFunc(string trxId) {
    S = S + " commitFun";
    log:printInfo("commitFunc");
}

public function abortFunc(string trxId) {
    S = S + " abortFunc";
    log:printInfo("abortFunc");
}


@transactions:Participant {
}
public function erroredFunc() {
    S = S + " in-participantErroredFunc";
    int k = 5;
    if (k == 5) {
        error err = { message: "TransactionError" };
        throw err;
    }
}

boolean thrown1 = false;
boolean thrown2 = false;

function initiatorFunc(boolean error1, boolean error2) returns string {
    transaction with retries=2 {
        S = S + " in-trx-block";
        participantFoo();
        try {
            if (!thrown1 && error1) {
                thrown1 = true;
                erroredFunc();
            }
            if (thrown1 && !thrown2 && error2) {
                thrown2 = true;
                erroredFunc();
            }
        } catch (error err) {
            S = S + " catched-error";
        }
        S = S + " in-trx-lastline";
    } onretry {
        S = S + " onretry-block";
    } committed {
        S = S + " committed-block";
    } aborted {
        S = S + " aborted-block";
    }
    S = S + " after-trx";
    return S;
}

function blowUp()  returns int {
    if (5 == 5) {
        error err = { message: "TransactionError" };
        throw err;
    }
    return 5;
}


