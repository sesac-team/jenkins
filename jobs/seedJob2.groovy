pipelineJob('example-pipeline2') {
  definition {
    cps {
      script('''
        pipeline {
          agent any
          stages {
            stage('Example') {
              steps {
                echo 'Hello, World! copy'
              }
            }
          }
        }
      ''')
    }
  }
}