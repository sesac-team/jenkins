pipelineJob('edgeServicePipeline') {
    triggers{
        githubPush()
    }
    definition {
        cps {
            script('''
pipeline {
    agent none
    environment {
        AWS_REGION = 'ap-northeast-2'
        SERVICE_NAME = 'edge-service'
        PROJECT_NAME = "fullaccel"
        ECR_DOMAIN = "516607723507.dkr.ecr.${AWS_REGION}.amazonaws.com"
        ECR_REPOSITORY = "${ECR_DOMAIN}/${PROJECT_NAME}/${SERVICE_NAME}"
        GIT_REPO_URL = "https://github.com/sesac-team/${SERVICE_NAME}.git"
        GIT_BRANCH = 'main'
    }

    stages {

    stage('Start'){
        agent any
        steps {
            slackSend(channel: '#ci-cd', color: '#FFFF00', message: "STARTED PIPELINE: '${env.JOB_NAME}:${env.BUILD_NUMBER}' ${env.BUILD_URL}")
        }
    }
        stage('Checkout') {
            agent {
                docker { image 'bitnami/git:latest' }
            }
            steps {
                checkout([
                    $class: 'GitSCM',
                    branches: [[name: "${GIT_BRANCH}"]],
                    userRemoteConfigs: [[url: "${GIT_REPO_URL}"]]
                ])
            }
        }

        stage('Build') {
            agent {
                docker { image 'gradle:8.10.0-jdk17-alpine' }
            }
            steps {
                sh 'chmod +x ./gradlew'
                sh './gradlew clean build -x test'
            }
            post {
                success {
                    archiveArtifacts artifacts: 'build/libs/*.jar', allowEmptyArchive: true
                }
            }
        }

        stage('Build Docker Image & Push to ECR') {
            agent {
                label "controller"
            }
            steps {
                script {
                    app = docker.build("${ECR_REPOSITORY}")
                    docker.withRegistry("https://${ECR_DOMAIN}", 'ecr:${AWS_REGION}:AWS_ACCESS_KEY') {
                        app.push("${BUILD_NUMBER}")
                        app.push("latest")
                    }
                }
            }
        }
        stage('Update Deployment Repo & Push') {
            agent {
                label "controller"
            }
            environment {
                GITHUB_USER = 'asusikai'
                GITHUB_EMAIL = 'adj0707@gmail.com'
                GITHUB_ID = 'sesac-team'
                DEPLOY_REPO_URL = "https://github.com/${GITHUB_ID}/deployment.git"
            }
            steps {
                git branch: "${GIT_BRANCH}", credentialsId: 'github-token', url: "${DEPLOY_REPO_URL}"
                sh "git config --global --add safe.directory ${workspace}"
                sh "git config --global user.name ${GITHUB_USER}"
                sh "git config --global user.email ${GITHUB_EMAIL}"
                sh 'sed -i "s|image:.*|image:${ECR_REPOSITORY}:${BUILD_NUMBER}|g" ${SERVICE_NAME}/deployment.yaml'
                sh 'git add ${SERVICE_NAME}/deployment.yaml'
                sh 'git commit -m "${SERVICE_NAME} Build. Build_NUMBER-${BUILD_NUMBER}"'
                withCredentials([gitUsernamePassword(credentialsId: 'github-token', gitToolName:'Default')]) {
                    sh 'git push --set-upstream origin ${GIT_BRANCH}'
                }
            }
        }
    }
    post{
        success{
            slackSend(channel: '#ci-cd', color: '#00FF00', message: "SUCCESS PIPELINE: '${env.JOB_NAME}:${env.BUILD_NUMBER}' ${env.BUILD_URL}")
        }
        failure{
            slackSend(channel: '#ci-cd', color: '#FF0000', message: "FAILED PIPELINE: '${env.JOB_NAME}:${env.BUILD_NUMBER}' ${env.BUILD_URL}")
        }
    }
}       
            ''')
            sandbox()
        }
    }
}
