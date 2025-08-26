pipeline {
  agent {
    kubernetes {
      label 'ci-cd-pipeline'
      yaml """
apiVersion: v1
kind: Pod
spec:
  containers:
    - name: maven
      image: maven:3.9.4-eclipse-temurin-17
      command: ['cat']
      tty: true
      volumeMounts:
        - name: maven-cache
          mountPath: /root/.m2

    - name: kaniko
      image: gcr.io/kaniko-project/executor:latest
      command: ["/busybox/sh"]
      args: ["-c", "sleep infinity"]
      tty: true
      volumeMounts:
        - name: kaniko-cache
          mountPath: /workspace

    - name: trivy
      image: aquasec/trivy:0.51.1
      command: ['cat']
      tty: true
      volumeMounts:
        - name: trivy-cache
          mountPath: /root/.cache/trivy

    - name: gitops
      image: alpine/git
      command: ['sh', '-c', 'apk add --no-cache curl bash && curl -L https://github.com/mikefarah/yq/releases/latest/download/yq_linux_amd64 -o /usr/bin/yq && chmod +x /usr/bin/yq && cat']
      tty: true

  volumes:
    - name: maven-cache
      persistentVolumeClaim:
        claimName: maven-cache-pvc
    - name: kaniko-cache
      emptyDir: {}
    - name: trivy-cache
      persistentVolumeClaim:
        claimName: trivy-cache-pvc
"""
      defaultContainer 'maven'
    }
  }

  environment {
    SONAR_HOST_URL   = 'http://sonarqube-sonarqube.sonarqube.svc.cluster.local:9000'
    SONAR_PROJECT_KEY = 'demo-scan'
    SONAR_PROJECT_NAME = 'demo-scan'
    SONAR_TOKEN      = credentials('sonar-token')
    DOCKERHUB        = credentials('dockerhub')
    IMAGE_TAG        = "tudaolw/test:${env.BUILD_NUMBER}"
    GITOPS_REPO      = 'gitops-jsp-test'
  }

  stages {

    stage('Checkout') {
      steps {
        git credentialsId: 'github-token',
            url: 'https://github.com/TuDaoLW/jsp-test.git',
            branch: 'master'
      }
    }

    stage('Build & Unit Test + SonarQube') {
      steps {
        container('maven') {
          dir('/workspace') {
            sh """
              mvn clean verify sonar:sonar \
                -DskipTests=false \
                -Dsonar.projectKey=${SONAR_PROJECT_KEY} \
                -Dsonar.projectName=${SONAR_PROJECT_NAME} \
                -Dsonar.host.url=${SONAR_HOST_URL} \
                -Dsonar.token=${SONAR_TOKEN}
            """
          }
        }
      }
    }

    stage('Build Image (Kaniko TAR)') {
      steps {
        container('kaniko') {
          sh """
            /kaniko/executor \
              --context /workspace \
              --dockerfile /workspace/Dockerfile \
              --tarPath=/workspace/app.tar \
              --no-push
          """
        }
      }
    }

    stage('Scan Image with Trivy') {
      steps {
        container('trivy') {
          sh """
            trivy image --input /workspace/app.tar \
              --severity CRITICAL,HIGH \
              --exit-code 1
          """
        }
      }
    }

    stage('Push Image (Kaniko)') {
      steps {
        container('kaniko') {
          sh """
            /kaniko/executor \
              --context /workspace \
              --dockerfile /workspace/Dockerfile \
              --destination docker.io/${IMAGE_TAG} \
              --cache=true \
              --skip-tls-verify
          """
        }
      }
    }

    stage('Update GitOps Manifest') {
      steps {
        container('gitops') {
          withCredentials([usernamePassword(credentialsId: 'github-tokem', usernameVariable: 'GIT_USER', passwordVariable: 'GIT_PASS')]) {
            sh '''
              git config --global user.name "jenkins"
              git config --global user.email "jenkins@local"

              git clone https://${GIT_USER}:${GIT_PASS}@github.com/tudaolw/${GITOPS_REPO}
              cd ${GITOPS_REPO}

              echo "Before update:"
              yq '.spec.template.spec.containers[0].image' deployment.yaml

              yq -i '.spec.template.spec.containers[0].image = "tudaolw/test:'"$BUILD_NUMBER"'"' deployment.yaml

              echo "After update:"
              yq '.spec.template.spec.containers[0].image' deployment.yaml

              git add deployment.yaml
              git commit -m "Update image tag to $BUILD_NUMBER"
              git push origin main
            '''
          }
        }
      }
    }
  }

  post {
    always {
      mail to: 'dtu951@gmail.com',
           subject: "Jenkins Build #${env.BUILD_NUMBER} - ${currentBuild.currentResult}",
           body: "Pipeline result: ${currentBuild.currentResult}\nCheck console: ${env.BUILD_URL}"
    }
  }
}
