pipelineJob('CI_Pipeline') {
    definition {
        cps {
            script('''

                pipeline {
                    agent none

                    environment {
                        AWS_REGION = 'ap-northeast-2'
                        ECR_REPOSITORY = '516607723507.dkr.ecr.ap-northeast-2.amazonaws.com/jenkins/jenkins-test-image'
                        GIT_REPO_URL = 'https://github.com/sesac-team/edge-service.git'
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
                            agent{
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
                                    dockerImage = docker.build("${ECR_REPOSITORY}:${BUILD_NUMBER}", "-f Dockerfile .")
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
                                        aws ecr get-login-password --region ap-northeast-2 | docker login --username AWS --password-stdin 516607723507.dkr.ecr.ap-northeast-2.amazonaws.com
                                        docker push ${ECR_REPOSITORY}:${BUILD_NUMBER}
                                    """
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
