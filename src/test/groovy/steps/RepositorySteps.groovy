package steps

import static io.restassured.RestAssured.given

import tests.TestSetup

class RepositorySteps extends TestSetup {

    static getHealthCheckResponse(artifactoryURL) {
        return given()
                .when()
                .get("${artifactoryURL}/router/api/v1/system/health")
                .then()
                .extract().response()
    }

    static ping(artifactoryURL) {
        return given()
                .when()
                .get("${artifactoryURL}/api/system/ping")
                .then()
                .extract().response()
    }

    static setBaseUrl(artifactoryURL, username, password, String baseUrl) {
        return given()
                .auth()
                .preemptive()
                .basic(username, password)
                .header('Cache-Control', 'no-cache')
                .header('content-Type', 'text/plain')
                .body(baseUrl)
                .when()
                .put("${artifactoryURL}/api/system/configuration/baseUrl")
                .then()
                .extract().response()
    }

    static createRepository(artifactoryURL, username, password, repoKey, body) {
        return given()
                .auth()
                .preemptive()
                .basic(username, password)
                .header('Cache-Control', 'no-cache')
                .header('content-Type', 'application/json')
                .body(body)
                .when()
                .put("${artifactoryURL}/api/repositories/${repoKey}")
                .then()
                .extract().response()
    }

    static getInfo(artifactoryURL, username, password, path) {
        return given()
                .auth()
                .preemptive()
                .basic(username, password)
                .header('Cache-Control', 'no-cache')
                .header('Content-Type', 'application/json')
                .when()
                .get("${artifactoryURL}/api/storage/" + path + '?properties')
                .then()
                .extract().response()
    }

    static deleteProperties(artifactoryURL, username, password, path, properties) {
        return given()
                .auth()
                .preemptive()
                .basic(username, password)
                .header('Cache-Control', 'no-cache')
                .header('Content-Type', 'application/json')
                .when()
                .delete("${artifactoryURL}/api/storage/" + path + '?properties=' + properties)
                .then()
                .extract().response()
    }

    static copyArtifact(artifactoryURL, username, password, srcRepoKey, srcFilePath, destRepoKey) {
        return given()
                .auth()
                .preemptive()
                .basic(username, password)
                .header('Cache-Control', 'no-cache')
                .header('content-Type', 'application/json')
                .body('')
                .post("${artifactoryURL}/api/copy/${srcRepoKey}/${srcFilePath}?to=/${destRepoKey}/${srcFilePath}")
                .then()
                .extract().response()
    }

}
