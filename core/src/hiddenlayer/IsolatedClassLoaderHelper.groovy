package hiddenlayer

import groovy.transform.CompileDynamic
import org.slf4j.Logger

import java.nio.file.Files
import java.nio.file.Paths

/**
 * Helper to create an isolated classloader for the HiddenLayer SDK
 * This prevents Kotlin classloader conflicts with Artifactory's runtime
 */
@CompileDynamic
class IsolatedClassLoaderHelper {

    static URLClassLoader createIsolatedClassLoader(String pluginsLibDir, Logger log) {
        try {
            def libPath = Paths.get(pluginsLibDir)
            if (!Files.exists(libPath)) {
                log.error("Plugins lib directory does not exist: ${pluginsLibDir}")
                return null
            }

            // Find all JARs in plugins/lib directory
            List<URL> urls = []
            Files.list(libPath).each { path ->
                if (path.toString().endsWith('.jar')) {
                    urls.add(path.toUri().toURL())
                    log.debug("Adding to isolated classloader: ${path.fileName}")
                }
            }

            log.info("Creating isolated classloader with ${urls.size()} JARs")

            // Create a new URLClassLoader with the parent set to the system classloader
            // This isolates it from Artifactory's ParallelWebappClassLoader
            return new URLClassLoader(
                urls.toArray(new URL[0]),
                ClassLoader.getSystemClassLoader()
            )
        } catch (Exception e) {
            log.error("Failed to create isolated classloader", e)
            return null
        }
    }

    static Object loadHiddenLayerClient(URLClassLoader classLoader, String apiUrl, String clientId, String clientSecret, Logger log) {
        try {
            // Load classes from the isolated classloader
            Class<?> builderClass = classLoader.loadClass('com.hiddenlayer.api.client.okhttp.HiddenLayerOkHttpClient')
            
            // Call HiddenLayerOkHttpClient.builder()
            Object builder = builderClass.getMethod('builder').invoke(null)
            
            // Set properties: baseUrl, clientId, clientSecret
            builder.getClass().getMethod('baseUrl', String.class).invoke(builder, apiUrl)
            builder.getClass().getMethod('clientId', String.class).invoke(builder, clientId)
            builder.getClass().getMethod('clientSecret', String.class).invoke(builder, clientSecret)
            
            // Call build()
            Object client = builder.getClass().getMethod('build').invoke(builder)
            
            log.info("Successfully created HiddenLayer client with isolated classloader")
            return client
            
        } catch (Exception e) {
            log.error("Failed to load HiddenLayer client with isolated classloader", e)
            throw e
        }
    }
}

