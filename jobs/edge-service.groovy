pipelineJob('CI_Pipeline') {
    definition {
        cps {
            script('''
pipeline {
    agent none

    environment {
        AWS_REGION = 'ap-northeast-2'
        SERVICE_NAME = 'edge-service'
        ECR_REPOSITORY = "516607723507.dkr.ecr.ap-northeast-2.amazonaws.com/fullaccel/${SERVICE_NAME}"
        GIT_REPO_URL = "https://github.com/sesac-team/${SERVICE_NAME}.git"
        GIT_BRANCH = 'main'
    }

    stages {
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

        stage('Build Docker Image') {
            agent {
                label "controller"
            }
            steps {
                script {
                    def imageId = sh(script: "docker build -f Dockerfile -q .", returnStdout: true).trim()
                    sh """
                        docker tag ${imageId} ${ECR_REPOSITORY}:${BUILD_NUMBER}
                        docker tag ${imageId} ${ECR_REPOSITORY}:latest
                    """
                }
            }
        }

        stage('Push to ECR') {
            agent {
                label "controller"
            }
            steps {
                script {
                    sh """
                        aws ecr get-login-password --region ${AWS_REGION} | docker login --username AWS --password-stdin ${ECR_REPOSITORY}
                        docker push ${ECR_REPOSITORY}:${BUILD_NUMBER}
                        docker push ${ECR_REPOSITORY}:latest
                    """
                }
            }
        }

        stage('Update Deployment Repo & Push') {
            agent {
                docker { image 'bitnami/git:latest' }
            }
            environment {
                GITHUB_USER = 'asusikai'
                GITHUB_EMAIL = 'adj0707@gmail.com'
                GITHUB_ID = 'sesac-team'
                DEPLOY_REPO_URL = "https://github.com/${GITHUB_ID}/deployment.git"
            }
            steps {
                git branch: "main", credentialsId: 'github-token', url: "${DEPLOY_REPO_URL}"
                sh "git config --global --add safe.directory ${workspace}"
                sh "git config --global user.name ${GITHUB_USER}"
                sh "git config --global user.email ${GITHUB_EMAIL}"
                sh 'sed -i "s/image:.*/image:${ECR_REPOSITORY}:${BUILD_NUMBER}/g" ${SERVICE_NAME}/deployment.yaml'
                sh 'git add ${SERVICE_NAME}/deployment.yaml'
                sh 'git commit -m "${SERVICE_NAME} Build. Build_NUMBER-${BUILD_NUMBER}"'
                withCredentials([usernamePassword(credentialsId: 'github-token', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
                    sh 'git push --set-upstream origin main'
                }
            }
        }
    }
}            
            ''')
            sandbox()
        }
    }
}
