pipeline {
  agent {
    kubernetes {
      label 'maven-agent'
      yaml """
apiVersion: v1
kind: Pod
metadata:
  labels:
    some-label: maven
spec:
  containers:
    - name: maven
      image: maven:3.9.4-eclipse-temurin-17
      command:
        - cat
      tty: true
      volumeMounts:
        - name: maven-cache
          mountPath: /root/.m2
    - name: kaniko
      image: gcr.io/kaniko-project/executor:latest
      command:
        - /busybox/sh
      args:
        - -c
        - "while true; do sleep 30; done"
  volumes:
    - name: maven-cache
      persistentVolumeClaim:
        claimName: maven-cache-pvc
"""
      defaultContainer 'maven'
    }
  }
  environment {
    SONAR_HOST_URL = 'http://sonarqube1.local'
    SONAR_PROJECT_KEY = 'demo-scan'
    SONAR_PROJECT_NAME = 'demo-scan'
    SONAR_TOKEN = credentials('sonar-token')
    DOCKERHUB = credentials('dockerhub')
    }

  stages {
    stage('Checkout') {
      steps {
        git credentialsId: 'github-token',
            url: 'https://github.com/TuDaoLW/test-app1.git',
            branch: 'master'
      }
    }
    stage('Build') {
      steps {
        container('maven') {
          sh 'mvn clean package -DskipTests'
        }
      }
    }
    stage('SonarQube Analysis') {
      steps {
        container('maven') {
          sh """
              mvn clean verify sonar:sonar \
                -Dsonar.projectKey=${SONAR_PROJECT_KEY} \
                -Dsonar.projectName='${SONAR_PROJECT_NAME}' \
                -Dsonar.host.url=${SONAR_HOST_URL} \
                -Dsonar.token=${SONAR_TOKEN}
            """
          }
        }
    }
stage('Build Docker Image with Kaniko') {
  environment {
    IMAGE = "docker.io/${DOCKERHUB_USR}/test:${BUILD_NUMBER}"
  }
  steps {
    container('kaniko') {
      sh '''
        echo $DOCKERHUB_USR
        echo $DOCKERHUB_PSW
        echo "{\"auths\":{\"https://index.docker.io/v1/\":{\"username\":\"$DOCKERHUB_USR\",\"password\":\"$DOCKERHUB_PSW\"}}}" > /kaniko/.docker/config.json

        /kaniko/executor \
          --dockerfile=Dockerfile \
          --context=dir://$(pwd) \
          --destination=$IMAGE \
          --destination=docker.io/$DOCKERHUB_USR/test:latest \
          --skip-tls-verify
      '''
    }
  }
}


  }

}

