import org.artifactory.exception.CancelException
import org.artifactory.repo.RepoPath
import org.artifactory.request.Request

import hiddenlayer.Api
import hiddenlayer.Auth
import hiddenlayer.Config
import hiddenlayer.models.ModelInfo
import hiddenlayer.ModelScanner

config = new Config(ctx)
api = new Api(config, log)
auth = new Auth(config, log)
modelScanner = new ModelScanner(config, api, log)

ARTIFACT_STATUS_SAFE = 'SAFE'
ARTIFACT_STATUS_UNSAFE = 'UNSAFE'
ARTIFACT_STATUS_PENDING = 'PENDING'

download {
    altResponse { Request request, RepoPath responseRepoPath ->
        log.info "altResponse: $responseRepoPath"
        try {
            if (!modelScanner.shouldScanRepo(responseRepoPath.repoKey)) {
                return
            }

            def repoConfig = repositories.getRepositoryConfiguration(responseRepoPath.repoKey)
            def packageType = repoConfig.getPackageType()
            def type = repoConfig.getType()
            log.info "altResponse: repo config: $type $packageType"

            def artifactStatus = repositories.getProperties(responseRepoPath).getFirst('hiddenlayer.status')
            log.info "file: $responseRepoPath status: $artifactStatus"
            if (artifactStatus == ARTIFACT_STATUS_UNSAFE) {
                status = HttpURLConnection.HTTP_NOT_FOUND
                message = 'Artifact is unsafe'
            }
            if (artifactStatus == ARTIFACT_STATUS_PENDING) {
                status = HttpURLConnection.HTTP_NOT_FOUND
                message = 'Artifact is being scanned by hiddenlayer'
            }

            if (artifactStatus != ARTIFACT_STATUS_SAFE) {
                // altResponse is called first, then beforeDownload is called
                // If we get to here, we have not started a scan yet and should allow altResponse to proceed so
                // that beforeDownload is called.
                // beforeDownload will throw a CancelException if needed based on scan results
                // If it cannot determine the scan results and config.scanDecisionMissing is 'deny', 
                // it will throw a CancelException

                log.info('Artifact has not been scanned yet')
            }
        } catch (Exception e) {
            log.error "Error handling altResponse: $e"
            throw e
        }
    }

    beforeDownload { Request request, RepoPath responseRepoPath ->
        log.info "beforeDownload: $responseRepoPath"
        try {
            if (!modelScanner.shouldScanRepo(responseRepoPath.repoKey)) {
                return
            }

            ModelInfo modelInfo = modelScanner.parseModelInfo(responseRepoPath)
            def properties = repositories.getProperties(responseRepoPath)
            log.info "file: $responseRepoPath properties: $properties"
            def artifactStatus = repositories.getProperties(responseRepoPath).getFirst('hiddenlayer.status')
            String sensorId = modelScanner.getSensorIdForUrl(modelInfo.repoPath)

            if (artifactStatus == ARTIFACT_STATUS_UNSAFE) {
                log.warn "Attempted to download unsafe file $responseRepoPath"
                throw new CancelException('Artifact is unsafe', HttpURLConnection.HTTP_NOT_FOUND)
            }
            if (artifactStatus == ARTIFACT_STATUS_PENDING && sensorId) {
                throw new CancelException('Artifact is being scanned by hiddenlayer', HttpURLConnection.HTTP_NOT_FOUND)
            }
            if (artifactStatus != ARTIFACT_STATUS_SAFE || (artifactStatus == ARTIFACT_STATUS_PENDING && !sensorId)) {
                // Artifact has not been scanned. Starting the scan process.

                repositories.setProperty(responseRepoPath, 'hiddenlayer.status', ARTIFACT_STATUS_PENDING)
                def content = repositories.getContent(responseRepoPath)
                modelScanner.submitHiddenLayerScan(modelInfo, content)
                String modelStatus = modelScanner.getHiddenLayerStatus(modelInfo)
                if (!modelStatus) {
                    log.error "Failed to get model status for file $responseRepoPath"
                    if (config.scanMissingRetry == true) {
                        modelScanner.startMissingScanOnBackground(responseRepoPath)
                    }
                    if (config.scanDecisionMissing == 'deny') {
                        throw new CancelException('Artifact has not been scanned by hiddenlayer', HttpURLConnection.HTTP_NOT_FOUND)
                    }
                    return
                }
                log.debug "file: $responseRepoPath status: $modelStatus"
                repositories.setProperty(responseRepoPath, 'hiddenlayer.status', modelStatus)
                if (config.deleteAfterScan && api.isSaaS()) {
                    sensorId = modelScanner.getSensorIdForUrl(modelInfo.repoPath)
                    api.deleteModel(sensorId)
                }
                if (modelStatus == ARTIFACT_STATUS_UNSAFE) {
                    log.warn "Attempted to download unsafe file $responseRepoPath"
                    throw new CancelException('Artifact is unsafe', HttpURLConnection.HTTP_NOT_FOUND)
                }
            }
        } catch (Exception e) {
            log.error "Error handling beforeDownload: $e"

            throw e
        }
    }
}
