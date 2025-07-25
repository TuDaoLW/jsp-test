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
    - name: buildah
      image: quay.io/buildah/stable
      command: ['cat']
      tty: true
      securityContext:
        privileged: true
    - name: trivy
      image: aquasec/trivy:0.51.1
      command: ['cat']
      tty: true
  volumes:
    - name: maven-cache
      persistentVolumeClaim:
        claimName: maven-cache-pvc
"""
      defaultContainer 'maven'
    }
  }
  environment {
    SONAR_HOST_URL = 'http://sonarqube-sonarqube.sonaqube.svc.cluster.local:9000'
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
    stage('Build & Push Image (Buildah)') {
        environment {
          IMAGE_TAG = "$DOCKERHUB_USR/test:${env.BUILD_NUMBER}"
        }

      steps {
        container('buildah') {
          sh '''
            mv Dockerfile.bak Dockerfile
            ls -la
            echo "$DOCKERHUB_PSW" | buildah login -u "$DOCKERHUB_USR" --password-stdin docker.io
            buildah bud -t docker.io/$IMAGE_TAG .
            #buildah push docker.io/$IMAGE_TAG
          '''
        }
      }
    }
stage('Scan Image with Trivy') {
  steps {
    container('trivy') {
      sh '''
        #ping -c 4 www.youtube.com
        trivy image --severity CRITICAL,HIGH \
          --exit-code 1 \
          docker.io/tudaolw/test:8 || true
      '''
    }
  }
}

  }

}

