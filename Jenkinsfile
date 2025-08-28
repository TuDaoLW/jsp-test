pipeline {
  agent {
    kubernetes {
      label 'maven-agent'
      yaml readFile('podTemplate.yaml')
      defaultContainer 'maven'
    }
  }

  environment {
    SONAR_HOST_URL     = 'http://sonarqube-sonarqube.sonarqube.svc.cluster.local:9000'
    SONAR_PROJECT_KEY  = 'demo-scan'
    SONAR_PROJECT_NAME = 'demo-scan'
    SONAR_TOKEN        = credentials('sonar-token')
    DOCKERHUB          = credentials('dockerhub')
    IMAGE_NAME         = 'test'
    GITOPS_REPO        = 'gitops-jsp-test'
  }

  stages {
    stage('Checkout') {
      steps {
        git credentialsId: 'github-token',
            url: 'https://github.com/TuDaoLW/jsp-test.git',
            branch: 'master'
        script {
          sh 'git config --global --add safe.directory /home/jenkins/agent/workspace/test-pipeline'
          // fix ownership, Lấy short SHA
          def sha = sh(
            script: 'git rev-parse --short HEAD',
            returnStdout: true
          ).trim()
          // Gán cho env biến để các stage sau dùng chung
          env.COMMIT_HASH = sha
          env.IMAGE_TAG   = "${DOCKERHUB_USR}/${IMAGE_NAME}:${sha}"
          echo " Using IMAGE_TAG = ${env.IMAGE_TAG}"
        }
      }
    }

    stage('Build & Unit Test') {
      steps {
        container('maven') {
          sh """
            mvn clean verify sonar:sonar \\
              -DskipTests=false \\
              -Dsonar.projectKey=${SONAR_PROJECT_KEY} \\
              -Dsonar.projectName=${SONAR_PROJECT_NAME} \\
              -Dsonar.host.url=${SONAR_HOST_URL} \\
              -Dsonar.token=${SONAR_TOKEN}
          """
        }
      }
    }

    stage('Build Image (Buildah)') {
      steps {
        container('buildah') {
          sh '''
            # Build image và xuất ra file tar để scan local
            buildah bud --layers -t docker.io/$IMAGE_TAG . 
            buildah push --format docker $IMAGE_TAG docker-archive:/tmp/image.tar
          '''
        }
      }
    }

    stage('Scan Image with Trivy') {
      steps {
        container('trivy') {
          catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE'){
          sh '''
            ls -l /tmp
            # Scan file tar local trước khi ship lên registry
            trivy image \
              --cache-dir /root/.cache/trivy \
              --input /tmp/image.tar \
              --timeout 15m \
              --scanners vuln \
              --severity HIGH,CRITICAL \
              --exit-code 1 || true 
          '''
          }
        }
      }
    }

    stage('Push Image (Buildah)') {
      when {
        // Chạy chỉ khi scan exit code = 0
        expression { currentBuild.currentResult == 'SUCCESS' }
      }
      steps {
        container('buildah') {
          sh '''
            # Đăng nhập và push image thật lên Docker Hub
            echo "$DOCKERHUB_PSW" | buildah login -u "$DOCKERHUB_USR" --password-stdin docker.io
            buildah push docker.io/$IMAGE_TAG
          '''
        }
      }
    }
    
    stage('Update manifest repo') {
      steps {
        container('gitops') {
          withCredentials([usernamePassword(credentialsId: 'github-token', usernameVariable: 'GIT_USER', passwordVariable: 'GIT_PASS')]) {
            sh '''
              git config --global user.name "jenkins"
              git config --global user.email "jenkins@local"

              git clone https://${GIT_USER}:${GIT_PASS}@github.com/tudaolw/${GITOPS_REPO}
              cd ${GITOPS_REPO}

              echo "Trước khi cập nhật:"
              yq '.spec.template.spec.containers[0].image' deployment.yaml

              yq -i '.spec.template.spec.containers[0].image = "'"$IMAGE_TAG"'"' deployment.yaml

              echo "Sau khi cập nhật:"
              yq '.spec.template.spec.containers[0].image' deployment.yaml

              git add deployment.yaml
              git commit -m "Update image tag to $IMAGE_TAG"
              git push origin main
            '''
          }
        }
      }
    }
  }

post {
  always {
    container('buildah') {
      sh '''
        echo "Dọn dẹp local image sau build"

        # Xóa image vừa build (nếu còn)
        buildah rmi $IMAGE_TAG || true
      '''
    }

    /* Tuỳ chọn: gửi email thông báo kết quả
    mail to: 'dtu951@gmail.com',
         subject: "Jenkins Build #${env.BUILD_NUMBER} - ${currentBuild.currentResult}",
         body: "Pipeline result: ${currentBuild.currentResult}\nCheck console: ${env.BUILD_URL}"
  */}
}

}
