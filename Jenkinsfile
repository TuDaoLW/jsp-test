pipeline {
  agent any

  stages {
    stage('Checkout') {
      steps {
        git credentialsId: 'github-token',
            url: 'https://github.com/TuDaoLW/test-app1.git',
            branch: 'master'
      }
    }
  }
}
