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
                            agent { label 'gradle' }
                            steps {
                                checkout([
                                    $class: 'GitSCM',
                                    branches: [[name: "${GIT_BRANCH}"]],
                                    userRemoteConfigs: [[url: "${GIT_REPO_URL}"]]
                                ])
                            }
                        }

                        stage('Build') {
                            agent { label 'gradle' }
                            steps {
                                sh './gradlew clean build -x test'
                            }
                            post {
                                success {
                                    archiveArtifacts artifacts: 'build/libs/*.jar', allowEmptyArchive: true
                                }
                            }
                        }

                        stage('Test') {
                            agent { label 'gradle' }
                            steps {
                                sh './gradlew test'
                            }
                        }

                        stage('Build Docker Image') {
                            agent { label 'docker' }
                            steps {
                                script {
                                    dockerImage = docker.build("${ECR_REPOSITORY}:${GIT_BRANCH}-${BUILD_NUMBER}")
                                }
                            }
                        }

                        stage('Push to ECR') {
                            agent { label 'docker' }
                            steps {
                                withAWS(region: "${AWS_REGION}", credentials: 'aws-credentials-id') {
                                    script {
                                        sh """
                                            $(aws ecr get-login-password --region ${AWS_REGION} | docker login --username AWS --password-stdin ${ECR_REPOSITORY})
                                            docker push ${ECR_REPOSITORY}:${GIT_BRANCH}-${BUILD_NUMBER}
                                        """
                                    }
                                }
                            }
                        }
                    }

                    post {
                        always {
                            cleanWs()
                        }
                    }
                }
            ''')
            sandbox()
        }
    }
}
