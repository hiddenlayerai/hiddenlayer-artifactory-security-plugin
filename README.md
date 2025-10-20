# HiddenLayer Artifactory Model Security User Plugin

This plugin submits models downloaded from a HuggingFace repository to the HiddenLayer API for scanning.
The plugin will block the download if the model is flagged as malicious.

## Features

For repositories that the plugin monitors, when a file is requested, it will submit it to HiddenLayer for scanning.
It will mark the the artifact in the repository with a property `hiddenlayer.status` as either `SAFE` or `UNSAFE`.

This plugin is configurable and will work with both local and remote artifactory repositories.
This plugin can also be configured to use the HiddenLayer SaaS platform or a self-hosted enterprise instance
of the model scanning service.

## Installation

This plugin has dependencies on the HiddenLayer SDK. This can be retrieved by running `gradle copy_deps` which will put all dependencies in 
`./build/deps`. Copy the HiddenLayer dependencies to `$JFROG_HOME/artifactory/var/etc/artifactory/plugins/lib`

This plugin needs to be added to the `$JFROG_HOME/artifactory/var/etc/artifactory/plugins` directory.
This can be done by copying the contentes of `./core/src` to the plugins directory.

## Configuration

The plugin can be configured by editing the `hiddenlayer.properties` file in the `$JFROG_HOME/artifactory/var/etc/artifactory/plugins` directory.

The following configuartion options are available:

* `hiddenlayer.auth.client_id` - The client ID for the HiddenLayer API. Required for SaaS, Optional for Enterprise
* `hiddenlayer.auth.client_secret` - The client secret for the HiddenLayer API. Required for SaaS, Optional for Enterprise
* `hiddenlayer.api.url` - The URL for the HiddenLayer API. Required. Change to the URL of your enterprise instance if using an enterprise instance.
* `hiddenlayer.api.version` - The version of the HiddenLayer API. Required. Defaults to `v2`
* `hiddenlayer.scan.repo_ids` - A comma separated list of repository IDs to scan. Required
* `hiddenlayer.scan.decision_missing` - The decision to make if the `hiddenlayer.status` property is missing. Optional. Set to `deny` or `allow`.
* `hiddenlayer.scan.missing_decision_retry` - If the scan cannot complete or fails during the intial request, this option will determine if the
  plugin will attempt to rescan the file. This may lead to excessive API calls. Optional. Set to `true` or `false`. Defaults to `false`.
* `hiddenlayer.scan.delete_adhoc_models_after_scan` - If the plugin should delete the adhoc model from the HiddenLayer platform after the scan is
  complete. Note: This feature is only available against SaaS versions of the model scanner. Optional. Set to `true` or `false`. Defaults to `true`.

## Development

### Getting Started

> [!IMPORTANT]
> We use Devcontainers to provide a consistent IDE experience
> Please follow the IDE Bootstrap steps to ensure that you are running in a devcontainer.

#### IDE Bootstrap

1. Install VSCode Extension: https://marketplace.visualstudio.com/items?itemName=ms-vscode-remote.remote-containers
2. cmd-shift-p -> "Dev Containers: Reopen In Container" (or click the blue array at the bottom-left corner)
3. `gh auth login`
4. `gh auth setup-git`
5. `git config --global user.email "[username_here]@hiddenlayer.com" && git config --global user.name "[first_name] [last_name]"`

To facilitate testing and setup, the following environment variables will be brought into the devcontainer environment:

* HL_CLIENT_ID - The client ID for the HiddenLayer API
* HL_CLIENT_SECRET - The client secret for the HiddenLayer API

#### Running the Plugin

1. Start Artifactory Container
```bash
docker compose up
```
2. Navigate to `localhost:8082` to see the 
   - If prompted enter a new password. (i.e. `Fr0gg3r`)
3. Click on the banner to activate Artifactory.
![activate-banner](./banner.png)
4. Enter a License and click `Save`.
5. Set up a new HuggingFace Repository
   - Click on `Welcome` in the top right corner
   - Click on `New Remote Repository`
   - Select `HuggingFace`
   - Set the repository key (something like `hf`)
   - Click `Create Remote Repository`
6. Configure the plugin
   - Open the file `./core/src/hiddenlayer.properties`
   - Add your hiddenlayer API auth client_id and client_secret
   - record the repository IDs for the repositories you want to scan
7. Reload the plugin
    ```bash
    curl -XPOST -uadmin:<password> localhost:8081/artifactory/api/plugins/reload
    ```

Now, whenever you download a model using the HuggingFace cli, the plugin will upload the model
to the HiddenLayer API for scanning.  Based on your configuration, the plugin will either block
the download or allow it to proceed.

#### Running automated tests

1. Ensure the Artifactory container is running
```bash
docker compose up
```
2. Run the tests
```bash
gradle clean
gradle copy_deps
gradle artifactory_common
gradle modelscanner
```

The `artifactory_common` set of tests will check the health of JFrog artifactory and install the license set in the environment variable `HL_LICENSE_KEY`.
The `modelscanner` set of tests will set up a remote huggingface repository and attempt to download a huggingface model from it.

#### Environment variables

The following environment variables can be configured and override the values in the `core/src/hiddenlayer.properties` file:

* HL_CLIENT_ID - The client ID for the HiddenLayer API
* HL_CLIENT_SECRET - The client secret for the HiddenLayer API
* HL_LICENSE_KEY - The license key for JFrog Artifactory
* HL_API_URL - The URL for the HiddenLayer API

### Resources

 - [User Plugins](https://jfrog.com/help/r/jfrog-integrations-documentation/user-plugins)
    - [Plugin Execution Points](https://jfrog.com/help/r/jfrog-integrations-documentation/plugin-execution-points)
 - [Docker Image](https://releases-docker.jfrog.io/ui/repos/tree/General/artifactory-pro/org)
   - [JFROG_HOME location in container](https://jfrog.com/help/r/jfrog-installation-setup-documentation/jfrog-home)
 - [Snyk Security Plugin](https://github.com/snyk/artifactory-snyk-security-plugin)