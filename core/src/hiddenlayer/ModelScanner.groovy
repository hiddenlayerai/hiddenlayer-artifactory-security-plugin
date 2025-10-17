package hiddenlayer

import com.hiddenlayer.api.client.HiddenLayerClient
import com.hiddenlayer.api.client.okhttp.HiddenLayerOkHttpClient
import com.hiddenlayer.api.lib.ScanFileOptions
import com.hiddenlayer.api.models.scans.results.ScanReport

import groovy.transform.CompileDynamic

import hiddenlayer.models.ModelInfo

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

import org.artifactory.repo.RepoPath
import org.artifactory.resource.ResourceStreamHandle

import org.slf4j.Logger
import java.security.SecureRandom

/**
 * ModelScanner class to scan models
 */
@CompileDynamic
class ModelScanner {

    Config config
    HiddenLayerClient client
    Logger log

    ModelScanner(Config config, HiddenLayerClient client, Logger log) {
        this.config = config
        this.client = client
        this.log = log
    }

    static ModelInfo parseModelInfo(RepoPath modelPath) {
        String[] modelPathParts = modelPath.toPath().split('/')
        String modelPublisher = modelPathParts[2]
        String modelName = modelPathParts[3]
        String modelVersion = modelPathParts[4]
        String fileName = modelPathParts[-1]
        return [
                modelPublisher: modelPublisher,
                modelName: modelName,
                modelVersion: modelVersion,
                fileName: fileName,
                repoPath: modelPath.toPath(),
        ]
    }

    static String parseModelStatus(ScanReport scanReport) {
        if (scanReport == null) {
            return null
        }

        if (scanReport.status() != ScanReport.Status.DONE) {
            return null
        }

        return scanReport.detectionCount() == 0 ? 'SAFE' : 'UNSAFE'
    }

    boolean shouldScanRepo(String repoKey) {
        try {
            String keyToCheck = repoKey
            String[] scanRepos = config.scanRepos
            if (repoKey.endsWith('-cache')) {
                keyToCheck = repoKey.substring(0, repoKey.length() - 6)
            }

            return scanRepos && scanRepos.contains(keyToCheck)
        } catch (Exception e) {
            log.error "Error checking if repo should be scanned: $e"
            throw e
        }
    }

    ScanReport submitHiddenLayerScan(ModelInfo modelInfo, ResourceStreamHandle content) {
        File tempFile = File.createTempFile('model-', '.tmp')
        tempFile.deleteOnExit()

        InputStream inputStream = content.inputStream
        Number size = content.size
        Number start_offset = 0
        while (start_offset < size) {
            long chunk_size = 8192
            byte[] buffer = new byte[(int) chunk_size]
            int bytesRead = inputStream.read(buffer, 0, (int) chunk_size)
            if (bytesRead == -1) {
                break
            }
            tempFile.withOutputStream { out ->
                out.write(buffer, 0, bytesRead)
            }
            start_offset += bytesRead
        }

        ScanFileOptions options = new ScanFileOptions(
            modelInfo.toSensorName(),
            tempFile.toPath().toString(),
            "1.0.0",
            true,
            "JFrog Artifactory",
            "",
        )
        ScanReport result = client.modelScanner().scanFile(options)
        return result
    }

    void startMissingScanOnBackground(RepoPath responseRepoPath) {
        Thread.start {
            ModelInfo modelInfo = modelScanner.parseModelInfo(responseRepoPath)
            repositories.setProperty(responseRepoPath, 'hiddenlayer.status', 'PENDING')
            ScanReport report = submitHiddenLayerScan(modelInfo)
            String modelStatus = parseModelStatus(report)
            if (!modelStatus) {
                log.error "Failed to get model status for file $responseRepoPath"
                return
            }
            log.debug "file: $responseRepoPath status: $modelStatus"
            repositories.setProperty(responseRepoPath, 'hiddenlayer.status', modelStatus)
            if (config.deleteAfterScan) {
                String modelId = getModelIdFromScanReport(report)
                if (modelId) {
                    client.models().delete(modelId)
                }
            }
        }
    }

    String getModelIdFromScanReport(ScanReport report) {
        if (report == null || report.inventory() == null) {
            return null
        }
        if (report.inventory().isScanModelComboV3()) {
            return report.inventory().asScanModelComboV3().modelId()
        } else if (report.inventory().isScanModelIdsV3()) {
            return report.inventory().asScanModelIdsV3().modelId()
        } else {
            return null
        }
    }
}
