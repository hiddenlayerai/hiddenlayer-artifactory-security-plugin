package tests

import static io.restassured.RestAssured.given
import static org.hamcrest.Matchers.equalTo
import static org.hamcrest.Matchers.nullValue

import groovy.json.JsonOutput
import io.restassured.response.Response
import org.junit.Assert
import org.testng.Reporter
import org.testng.annotations.Test
import steps.RepositorySteps
import steps.SecuritySteps

abstract class ModelScannerTest extends RepositorySteps {

    def artifactoryURL = "${artifactoryBaseURL}/artifactory"
    def securitySteps = new SecuritySteps()

    private String repoType
    private String repoKey
    private String remoteUrl

    String safeModel = 'prajjwal1/bert-tiny'
    private String safeFile = 'resolve/4746226cbb28f1e1c99977204b3ac22ecfe3a072/pytorch_model.bin'
    String unsafeModel = 'matanby/unsafe-diffusion'
    private String unsafeFile = 'resolve/243eb928376792047369d2ef072d03528c611909/diffusion_pytorch_model.bin'

    ModelScannerTest(String repoType, String repoKey, String remoteUrl) {
        this.repoType = repoType
        this.repoKey = repoKey
        this.remoteUrl = remoteUrl
    }

    @Test(priority=0, groups='model-scanner', testName = 'Set up HuggingFace repo')
    void setupHuggingFaceRepoTest() {
        Reporter.log("- Test: Set up $this.repoType HuggingFace repo", true)
        String body = JsonOutput.toJson([
            key: repoKey,
            rclass: repoType.toLowerCase(),
            packageType: 'huggingfaceml',
            url: remoteUrl
        ])
        Response response = createRepository(artifactoryURL, username, password, repoKey, body)
        if (response.getStatusCode() == 200) {
            Reporter.log('- HuggingFace repository created successfully', true)
        } else if (response.getStatusCode() == 400 && response.body().asString().contains('Case insensitive repository key already exists')) {
            Reporter.log('- HuggingFace repository already exists', true)
        } else {
            Reporter.log('- HuggingFace repository creation failed', true)
            Reporter.log("- Response: ${response.body().asString()}", true)
            Assert.fail()
        }
    }

    @Test(priority=1, groups='model-scanner', testName = 'Repo setup')
    void setupRepoForDownloadModelTests() {
    }

    @Test(priority=2, groups='model-scanner', testName = 'Download model from HuggingFace Repo')
    void downloadModelFromHuggingFaceTest() {
        Reporter.log("- Test: Download model from $this.repoType HuggingFace Repo", true)
        downloadModelTest(repoKey, safeModel, safeFile, true)
    }

    @Test(priority=3, groups='model-scanner', testName = 'Download unsafe file is prevented from HuggingFace Repo')
    void downloadUnsafeModelFromHuggingFaceTest() {
        Reporter.log("- Test: Download unsafe file from $this.repoType HuggingFace Repo", true)
        downloadModelTest(repoKey, unsafeModel, unsafeFile, false)
    }

    @Test(priority=4, groups='model-scanner', testName = 'Scan results are saved as properties')
    void scanResultsAreSavedAsPropertiesTest() {
        Reporter.log("- Test: Scan results are saved as properties for $this.repoType repo", true)
        def safePath = getSafePath()
        Response response = getInfo(artifactoryURL, username, password, safePath)
        response.then().assertThat().log().ifValidationFails().statusCode(200)
                .body("properties['hiddenlayer.status'][0]", equalTo('SAFE'))
        def unsafePath = getUnsafePath()
        response = getInfo(artifactoryURL, username, password, unsafePath)
        response.then().assertThat().log().ifValidationFails().statusCode(200)
                .body("properties['hiddenlayer.status'][0]", equalTo('UNSAFE'))
    }
    @Test(priority=5, groups='model-scanner', testName = 'Subsequent download attempts are determined using hiddenlayer status property')
    void downloadScannedModelFromRemoteHuggingFaceTest() {
        Reporter.log("- Test: Subsequent download attempts are determined using hiddenlayer status property in $this.repoType repo", true)
        downloadModelTest(repoKey, safeModel, safeFile, true)
        downloadModelTest(repoKey, unsafeModel, unsafeFile, false)
    }

    String getSafePath() {
        return "$repoKey/models/$safeModel/4746226cbb28f1e1c99977204b3ac22ecfe3a072_2021-10-27T18:29:01.000Z/model/pytorch_model.bin"
    }

    String getUnsafePath() {
        return "$repoKey/models/$unsafeModel/243eb928376792047369d2ef072d03528c611909_2024-03-19T19:05:00.000Z/model/diffusion_pytorch_model.bin"
    }

    void downloadModelTest(repo, model, file, isFileSafe) {
        Response response = securitySteps.createToken(artifactoryBaseURL, username, password)
        String token = ''
        if (response.statusCode == 200) {
            Reporter.log('- Token created successfully', true)
            token = response.jsonPath().getString('reference_token')
        } else {
            Reporter.log('- Token creation failed', true)
            Reporter.log("- Response: ${response.body().asString()}", true)
            Assert.fail()
        }

        response = given()
                .header('Authorization', "Bearer ${token}")
                .when()
                .get("${artifactURL}/artifactory/api/huggingfaceml/${repo}/api/models/${model}")
                .then()
                .extract().response()

        if (response.statusCode == 200) {
            Reporter.log('- Model downloaded successfully', true)
        } else {
            Reporter.log('- Model download failed', true)
            Reporter.log("- Response: ${response.body().asString()}", true)
            Assert.fail()
        }

        response = given()
                    .header('Authorization', "Bearer ${token}")
                    .when()
                    .get("${artifactURL}/artifactory/api/huggingfaceml/${repo}/${model}/${file}")
                    .then()
                    .extract().response()

        if (response.statusCode == 200) {
            Reporter.log('- File downloaded successfully', true)
            Assert.assertTrue(isFileSafe)
        } else if (response.getStatusCode() == 404 && !isFileSafe) {
            Reporter.log('- File download prevented', true)
            Assert.assertFalse(isFileSafe)
        } else {
            Reporter.log('- File download failed', true)
            Reporter.log("- Response: ${response.body().asString()}", true)
            Assert.fail()
        }
    }
}
