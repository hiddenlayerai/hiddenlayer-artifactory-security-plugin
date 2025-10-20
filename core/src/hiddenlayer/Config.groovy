package hiddenlayer

import groovy.transform.CompileDynamic
import org.artifactory.spring.InternalArtifactoryContext

/**
 * The Config class for HiddenLayer plugin
 */
@CompileDynamic
class Config {

    String clientId
    String clientSecret
    String apiUrl
    String[] scanRepos = []
    String scanDecisionMissing
    boolean scanMissingRetry
    boolean deleteAfterScan

    Config(InternalArtifactoryContext ctx) {
        loadConfig(ctx)
    }

    void loadConfig(InternalArtifactoryContext ctx) {
        Properties props = new Properties()
        props.load(new File("${ctx.artifactoryHome.etcDir}/plugins/hiddenlayer.properties").newDataInputStream())
        def config = new ConfigSlurper().parse(props).get('hiddenlayer')

        def auth = config.get('auth')
        def api = config.get('api')
        def scan = config.get('scan')

        clientId = System.getenv('HL_CLIENT_ID') != null ? System.getenv('HL_CLIENT_ID') : auth.get('client_id')
        clientSecret = System.getenv('HL_CLIENT_SECRET') != null
            ? System.getenv('HL_CLIENT_SECRET')
            : auth.get('client_secret')

        apiUrl = System.getenv('HL_API_URL') != null && System.getenv('HL_API_URL') != ''
            ? System.getenv('HL_API_URL')
            : api.get('url') as String
        String repos = scan.get('repo_ids') as String
        scanRepos = repos.split(',')
        scanDecisionMissing = scan.get('decision_missing') as String
        String retry = scan.get('missing_decision_retry') as String
        scanMissingRetry = retry != 'false'
        String delete = scan.get('delete_adhoc_models_after_scan') as String
        deleteAfterScan = delete != 'false'
    }

}
