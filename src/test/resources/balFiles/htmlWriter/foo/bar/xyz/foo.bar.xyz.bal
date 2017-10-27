package foo.bar.xyz;

import ballerina.doc;

struct Argument {
    string text;
    int argumentId;
    int sentiment;
}

@doc:Description{value:"Remove HTTP header from the message"}
@doc:Param{value:"m: Incoming message"}
@doc:Param{value:"key: HTTP header key"}
function removeHeader (message m, string key) {
    println("invoked");
    Argument arg1 = {text:"arg1", argumentId:1, sentiment:1};
    testStruct(arg1);
}

@doc:Description{value:"Test struct data type"}
@doc:Param{value:"argument: Incoming argument"}
function testStruct(Argument argument) {
    println("Hello, World!");
}


