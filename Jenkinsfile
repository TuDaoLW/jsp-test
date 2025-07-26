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
      volumeMounts:
        - name: trivy-cache
          mountPath: /root/.cache/trivy
  volumes:
    - name: maven-cache
      persistentVolumeClaim:
        claimName: maven-cache-pvc
    - name: trivy-cache
      persistentVolumeClaim:
        claimName: trivy-cache-pvc
"""
      defaultContainer 'maven'
    }
  }
  environment {
    SONAR_HOST_URL = 'http://sonarqube-sonarqube.sonarqube.svc.cluster.local:9000'
    SONAR_PROJECT_KEY = 'demo-scan'
    SONAR_PROJECT_NAME = 'demo-scan'
    SONAR_TOKEN = credentials('sonar-token')
    DOCKERHUB = credentials('dockerhub')
    IMAGE_TAG = "$DOCKERHUB_USR/test:${env.BUILD_NUMBER}"
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
      steps {
        container('buildah') {
          sh '''
            mv Dockerfile.bak Dockerfile
            ls -la
            echo "$DOCKERHUB_PSW" | buildah login -u "$DOCKERHUB_USR" --password-stdin docker.io
            buildah bud -t docker.io/$IMAGE_TAG .
            buildah push docker.io/$IMAGE_TAG
          '''
        }
      }
    }
stage('Scan Image with Trivy') {
  steps {
    container('trivy') {
      sh '''
        echo "skip to savetime"
        #trivy image --timeout 15m --scanners vuln --severity CRITICAL,HIGH \
        #  --exit-code 1 \
        #  docker.io/$IMAGE_TAG || true
      '''
    }
  }
}
stage('Update Deployment Manifest') {
  steps {
    container('maven') {
      withCredentials([usernamePassword(credentialsId: 'github-token', usernameVariable: 'GIT_USER', passwordVariable: 'GIT_TOKEN')]) {
        sh '''
          # Clone repo manifest
          git clone https://$GIT_USER:$GIT_TOKEN@github.com/tudaolw/test-app1-deploy.git
          cd test-app1-deploy

          # Update image tag in deployment.yaml
          yq e '.spec.template.spec.containers[0].image = "tudaolw/test:'"$BUILD_NUMBER"'"' -i deployment.yaml

          # Commit and push
          git config user.name "jenkins"
          git config user.email "ci@example.com"
          git add deployment.yaml
          git commit -m "Update image to tudaolw/test:$BUILD_NUMBER"
          git push origin main
        '''
      }
    }
  }
}

  }

}

