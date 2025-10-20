package hiddenlayer

import groovy.transform.CompileDynamic
import org.slf4j.Logger

/**
 * Wrapper that provides a clean API around the HiddenLayer SDK
 * loaded via isolated classloader (using pure reflection).
 * 
 * We CANNOT use strong typing because that would load SDK classes
 * in the wrong classloader, causing Kotlin conflicts.
 */
@CompileDynamic
class HiddenLayerClientWrapper {
    
    private final URLClassLoader isolatedClassLoader
    private final Logger log
    
    // Store the actual client (untyped to avoid classloader issues)
    private final Object actualClient
    
    // Cache commonly used classes
    private final Class<?> scanReportClass
    private final Class<?> scanReportStatusClass
    private final Object statusDoneValue
    
    HiddenLayerClientWrapper(URLClassLoader isolatedClassLoader, String apiUrl, String clientId, String clientSecret, Logger log) {
        this.isolatedClassLoader = isolatedClassLoader
        this.log = log
        
        log.info("Initializing HiddenLayer client with API URL: ${apiUrl}")
        
        try {
            // Load classes from isolated classloader
            Class<?> clientBuilderClass = isolatedClassLoader.loadClass('com.hiddenlayer.api.client.okhttp.HiddenLayerOkHttpClient')
            
            // Call HiddenLayerOkHttpClient.builder()
            def builderMethod = clientBuilderClass.getMethod('builder')
            Object builder = builderMethod.invoke(null)
            
            // Set properties
            builder.getClass().getMethod('baseUrl', String.class).invoke(builder, apiUrl)
            builder.getClass().getMethod('clientId', String.class).invoke(builder, clientId)
            builder.getClass().getMethod('clientSecret', String.class).invoke(builder, clientSecret)
            
            // Build client
            this.actualClient = builder.getClass().getMethod('build').invoke(builder)
            
            // Cache commonly used classes and values
            this.scanReportClass = isolatedClassLoader.loadClass('com.hiddenlayer.api.models.scans.results.ScanReport')
            this.scanReportStatusClass = isolatedClassLoader.loadClass('com.hiddenlayer.api.models.scans.results.ScanReport$Status')
            this.statusDoneValue = scanReportStatusClass.getField('DONE').get(null)
            
            log.info("HiddenLayer client created successfully via isolated classloader")
        } catch (Exception e) {
            log.error("Failed to create HiddenLayer client", e)
            log.error("API URL: ${apiUrl}")
            log.error("Client ID present: ${clientId != null && !clientId.isEmpty()}")
            log.error("Client Secret present: ${clientSecret != null && !clientSecret.isEmpty()}")
            throw e
        }
    }
    
    /**
     * Scan a file using the HiddenLayer API.
     * Returns a ScanResult wrapper with convenient methods.
     */
    ScanResult scanFile(String sensorName, String filePath, String version, boolean adhoc, String source, String additionalMetadata) {
        try {
            log.info("Starting scan for: ${sensorName}")
            
            // Create ScanFileOptions
            Class<?> optionsClass = isolatedClassLoader.loadClass('com.hiddenlayer.api.lib.ScanFileOptions')
            def constructor = optionsClass.getConstructor(
                String.class, String.class, String.class, 
                Boolean.TYPE, String.class, String.class
            )
            Object options = constructor.newInstance(
                sensorName, filePath, version,
                adhoc, source, additionalMetadata
            )
            
            // Call client.modelScanner().scanFile(options)
            def modelScannerMethod = actualClient.getClass().getMethod('modelScanner')
            def modelScanner = modelScannerMethod.invoke(actualClient)
            def scanFileMethod = modelScanner.getClass().getMethod('scanFile', optionsClass)
            Object scanReport = scanFileMethod.invoke(modelScanner, options)
            
            log.info("Scan completed for: ${sensorName}")
            
            // Wrap the result for easier use
            return new ScanResult(scanReport, scanReportClass, scanReportStatusClass, statusDoneValue, log)
            
        } catch (Exception e) {
            log.error("Error scanning file: ${sensorName}", e)
            log.error("File path: ${filePath}")
            
            if (e.class.name.contains('HiddenLayerInvalidDataException')) {
                log.error("API returned invalid data - check credentials and API URL")
            }
            throw e
        }
    }
    
    /**
     * Delete a model by ID.
     */
    void deleteModel(String modelId) {
        try {
            log.debug("Deleting model: ${modelId}")
            def modelsMethod = actualClient.getClass().getMethod('models')
            def models = modelsMethod.invoke(actualClient)
            def deleteMethod = models.getClass().getMethod('delete', String.class)
            deleteMethod.invoke(models, modelId)
            log.debug("Model deleted: ${modelId}")
        } catch (Exception e) {
            log.error("Error deleting model: ${modelId}", e)
            throw e
        }
    }
    
    /**
     * Wrapper class for scan results with convenient methods
     */
    @CompileDynamic
    static class ScanResult {
        private final Object scanReport
        private final Class<?> scanReportClass
        private final Class<?> statusClass
        private final Object statusDoneValue
        private final Logger log
        
        ScanResult(Object scanReport, Class<?> scanReportClass, Class<?> statusClass, Object statusDoneValue, Logger log) {
            this.scanReport = scanReport
            this.scanReportClass = scanReportClass
            this.statusClass = statusClass
            this.statusDoneValue = statusDoneValue
            this.log = log
        }
        
        /**
         * Check if scan is done
         */
        boolean isDone() {
            try {
                if (scanReport == null) return false
                def statusMethod = scanReport.getClass().getMethod('status')
                def status = statusMethod.invoke(scanReport)
                return status == statusDoneValue
            } catch (Exception e) {
                log.error("Error checking if scan is done", e)
                return false
            }
        }
        
        /**
         * Get detection count
         */
        int getDetectionCount() {
            try {
                if (scanReport == null) return -1
                def method = scanReport.getClass().getMethod('detectionCount')
                return method.invoke(scanReport) as int
            } catch (Exception e) {
                log.error("Error getting detection count", e)
                return -1
            }
        }
        
        /**
         * Check if the scan result is SAFE (no detections)
         */
        boolean isSafe() {
            return isDone() && getDetectionCount() == 0
        }
        
        /**
         * Check if the scan result is UNSAFE (has detections)
         */
        boolean isUnsafe() {
            return isDone() && getDetectionCount() > 0
        }
        
        /**
         * Get model ID from scan report
         */
        String getModelId() {
            try {
                if (scanReport == null) return null
                
                def inventoryMethod = scanReport.getClass().getMethod('inventory')
                def inventory = inventoryMethod.invoke(scanReport)
                if (inventory == null) return null
                
                // Try isScanModelComboV3()
                def isComboMethod = inventory.getClass().getMethod('isScanModelComboV3')
                if (isComboMethod.invoke(inventory) as boolean) {
                    def asComboMethod = inventory.getClass().getMethod('asScanModelComboV3')
                    def combo = asComboMethod.invoke(inventory)
                    def modelIdMethod = combo.getClass().getMethod('modelId')
                    return modelIdMethod.invoke(combo) as String
                }
                
                // Try isScanModelIdsV3()
                def isIdsMethod = inventory.getClass().getMethod('isScanModelIdsV3')
                if (isIdsMethod.invoke(inventory) as boolean) {
                    def asIdsMethod = inventory.getClass().getMethod('asScanModelIdsV3')
                    def ids = asIdsMethod.invoke(inventory)
                    def modelIdMethod = ids.getClass().getMethod('modelId')
                    return modelIdMethod.invoke(ids) as String
                }
                
                return null
            } catch (Exception e) {
                log.error("Error getting model ID", e)
                return null
            }
        }
        
        /**
         * Get the underlying scan report object (for advanced use)
         */
        Object getRawReport() {
            return scanReport
        }
    }
}
