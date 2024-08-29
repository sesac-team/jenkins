pipelineJob('example-pipeline') {
  definition {
    cps {
      script('''
        pipeline {
          agent any
          stages {
            stage('Example') {
              steps {
                echo 'testing sandbox'
              }
            }
          }
        }
      ''')
      sandbox(true)
    }
  }
}