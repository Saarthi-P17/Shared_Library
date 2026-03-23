def call(Map config) {

    node {

        def repoUrl      = config.repoUrl
        def branch       = config.branch ?: 'main'
        def appType      = config.appType ?: 'python'
        def enableTrivy  = config.enableTrivy ?: false
        def slackChannel = config.slackChannel ?: '#ci-operation-notifications'

        try {

            stage('Clean Workspace') {
                deleteDir()
            }

            stage('Checkout Code') {
                git branch: branch, url: repoUrl
            }

            if (appType == 'python') {

                stage('Setup Python Environment') {
                    sh '''
                    python3 -m venv venv
                    . venv/bin/activate
                    pip install --upgrade pip

                    if [ -f requirements.txt ]; then
                        pip install -r requirements.txt
                    else
                        echo "No requirements.txt found"
                    fi
                    '''
                }
            }

            if (enableTrivy) {

                stage('Install Trivy (Local)') {
                    sh '''
                    curl -sfL https://raw.githubusercontent.com/aquasecurity/trivy/main/contrib/install.sh | sh
                    export PATH=$PATH:$(pwd)/bin
                    '''
                }

                stage('Dependency Scan - Trivy') {
                    sh '''
                    export PATH=$PATH:$(pwd)/bin

                    mkdir -p reports

                    trivy fs . \
                    --severity HIGH,CRITICAL \
                    --format table \
                    --output reports/trivy.txt || true

                    cat reports/trivy.txt || true
                    '''
                }

                stage('Archive Reports') {
                    archiveArtifacts artifacts: 'reports/**', allowEmptyArchive: true
                }
            }

            currentBuild.result = 'SUCCESS'

        } catch (err) {

            currentBuild.result = 'FAILURE'
            throw err

        } finally {

            stage('Post Actions') {

                if (currentBuild.result == 'SUCCESS') {
                    slackSend(
                        channel: slackChannel,
                        color: 'good',
                        message: "SUCCESS\nJob: ${env.JOB_NAME}\nBuild: #${env.BUILD_NUMBER}\nURL: ${env.BUILD_URL}"
                    )
                } else {
                    slackSend(
                        channel: slackChannel,
                        color: 'danger',
                        message: "FAILED\nJob: ${env.JOB_NAME}\nBuild: #${env.BUILD_NUMBER}\nURL: ${env.BUILD_URL}"
                    )
                }

                cleanWs()
            }
        }
    }
}
