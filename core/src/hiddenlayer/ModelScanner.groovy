package hiddenlayer

import groovy.transform.CompileDynamic

import hiddenlayer.models.ModelInfo

import org.artifactory.repo.RepoPath
import org.artifactory.resource.ResourceStreamHandle

import org.slf4j.Logger

/**
 * ModelScanner class to scan models using HiddenLayer API
 * Uses HiddenLayerClientWrapper which provides a clean API over reflection-based SDK access
 */
@CompileDynamic
class ModelScanner {

    Config config
    HiddenLayerClientWrapper client
    Logger log

    ModelScanner(Config config, HiddenLayerClientWrapper client, Logger log) {
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

    static String parseModelStatus(HiddenLayerClientWrapper.ScanResult scanResult) {
        if (scanResult == null) {
            return null
        }

        if (!scanResult.isDone()) {
            return null
        }

        return scanResult.isSafe() ? 'SAFE' : 'UNSAFE'
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

    HiddenLayerClientWrapper.ScanResult submitHiddenLayerScan(ModelInfo modelInfo, ResourceStreamHandle content) {
        File tempFile = File.createTempFile('model-', '.tmp')
        tempFile.deleteOnExit()

        try {
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

            // Use wrapper's clean API (no reflection in our code!)
            HiddenLayerClientWrapper.ScanResult result = client.scanFile(
                modelInfo.toSensorName(),
                tempFile.toPath().toString(),
                "1.0.0",
                true,
                "JFrog Artifactory",
                ""
            )
            
            return result
        } catch (Exception e) {
            log.error("Error submitting HiddenLayer scan", e)
            throw e
        } finally {
            // Ensure temp file is cleaned up
            tempFile.delete()
        }
    }

    void startMissingScanOnBackground(RepoPath responseRepoPath, def repositories) {
        Thread.start {
            try {
                ModelInfo modelInfo = parseModelInfo(responseRepoPath)
                repositories.setProperty(responseRepoPath, 'hiddenlayer.status', 'PENDING')
                def content = repositories.getContent(responseRepoPath)
                
                HiddenLayerClientWrapper.ScanResult result = submitHiddenLayerScan(modelInfo, content)
                String modelStatus = parseModelStatus(result)
                
                if (!modelStatus) {
                    log.error "Failed to get model status for file $responseRepoPath"
                    return
                }
                
                log.debug "file: $responseRepoPath status: $modelStatus"
                repositories.setProperty(responseRepoPath, 'hiddenlayer.status', modelStatus)
                
                if (config.deleteAfterScan) {
                    String modelId = result.getModelId()
                    if (modelId) {
                        client.deleteModel(modelId)
                    }
                }
            } catch (Exception e) {
                log.error("Error in background scan", e)
            }
        }
    }
}
