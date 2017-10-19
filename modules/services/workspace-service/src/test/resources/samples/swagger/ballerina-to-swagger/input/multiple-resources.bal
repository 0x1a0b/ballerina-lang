import ballerina.net.http;
import ballerina.net.http.swagger;

@http:configuration {
    basePath:"/api"
}
@swagger:ServiceInfo {
    serviceVersion: "1.0.0",
    description: "A sample API that uses a petstore as an example to demonstrate features in the swagger-2.0 specification"
}
service<http> Service9 {

    @http:resourceConfig{
        methods: ["GET"],
        path: "/pets/{id}",
        produces: ["application/json"]
    }
    resource Resource1 (http:Request req, http:Response res, string id) {
    }

    @http:resourceConfig{
        methods: ["POST"],
        path: "/pets/{id}",
        produces: ["application/json"],
        consumes: ["application/json"]
    }
    resource Resource2 (http:Request req, http:Response res, string id) {
    }

    @http:resourceConfig{
        methods: ["PUT"],
        path: "/pets",
        consumes: ["application/json"]
    }
    resource Resource3 (http:Request req, http:Response res) {
    }

    @http:resourceConfig{
        methods: ["DELETE"],
        path: "/pets/{id}",
        produces: ["application/json"]
    }
    resource Resource4 (http:Request req, http:Response res) {
    }
}