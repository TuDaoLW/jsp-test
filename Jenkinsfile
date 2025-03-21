pipeline {
    agent {
        kubernetes {
            label 'spring-agent'
            serviceAccountName: 'jenkins'
            yaml '''
            apiVersion: v1
            kind: Pod
            spec:
              containers:
              - name: maven
                image: maven:3.8.5-openjdk-17
                command: ["/bin/sh", "-c", "sleep infinity"]
                workingDir: /home/jenkins/agent
                env:
                - name: HOME
                  value: /home/jenkins/agent
                volumeMounts:
                - name: workspace-volume
                  mountPath: /home/jenkins/agent
              - name: oc
                image: quay.io/openshift/origin-cli:4.15
                command: ["/bin/sh", "-c", "sleep infinity"]
                workingDir: /home/jenkins/agent
                volumeMounts:
                - name: workspace-volume
                  mountPath: /home/jenkins/agent
              - name: sonar
                image: sonarqube:10-community
                command: ["/bin/sh", "-c", "sleep infinity"]
                volumeMounts:
                - name: workspace-volume
                  mountPath: /home/jenkins/agent
              - name: depcheck
                image: owasp/dependency-check:latest
                command: ["/bin/sh", "-c", "sleep infinity"]
                volumeMounts:
                - name: workspace-volume
                  mountPath: /home/jenkins/agent
              - name: db
                image: postgres:13
                env:
                - name: POSTGRES_USER
                  value: "testuser"
                - name: POSTGRES_PASSWORD
                  value: "testpass"
                - name: POSTGRES_DB
                  value: "testdb"
                command: ["/bin/sh", "-c", "sleep infinity"]
                volumeMounts:
                - name: workspace-volume
                  mountPath: /home/jenkins/agent
              volumes:
              - name: workspace-volume
                emptyDir: {}
            '''
        }
    }
    triggers { pollSCM('H/5 * * * *') }  // Poll every 5 minutes
    environment {
        TEST_NS = 'test'
        IMAGE_NAME = "spring-boot-app:${env.BUILD_NUMBER}"
    }
    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }
        stage('Build') {
            steps {
                container('maven') {
                    sh 'mvn -B clean package -DskipTests'
                }
            }
        }
        stage('Unit Tests') {
            steps {
                container('maven') {
                    sh 'mvn test'
                }
            }
            post {
                always {
                    junit 'target/surefire-reports/*.xml'  // Archive test results
                }
            }
        }
        stage('Security Checks') {
            parallel {
                stage('Static Analysis') {
                    steps {
                        container('sonar') {
                            sh '''
                            sonar-scanner \
                              -Dsonar.projectKey=demo-app \
                              -Dsonar.sources=. \
                              -Dsonar.host.url=http://sonarqube:9000 \
                              -Dsonar.login=admin \
                              -Dsonar.password=admin
                            '''
                        }
                    }
                }
                stage('Dependency Scan') {
                    steps {
                        container('depcheck') {
                            sh '''
                            dependency-check.sh \
                              --project "Demo App" \
                              --scan . \
                              --out ./dependency-check-report.html \
                              --format HTML
                            '''
                        }
                    }
                    post {
                        always {
                            archiveArtifacts 'dependency-check-report.html'
                        }
                    }
                }
            }
        }
        stage('Integration Tests') {
            steps {
                container('db') {
                    sh '''
                    mkdir -p /var/lib/postgresql/data
                    pg_ctl initdb -D /var/lib/postgresql/data
                    pg_ctl -D /var/lib/postgresql/data -l logfile start
                    sleep 5  # Wait for DB to start
                    '''
                }
                container('maven') {
                    sh '''
                    mvn verify -Pintegration-tests \
                      -Ddb.host=localhost \
                      -Ddb.port=5432 \
                      -Ddb.user=testuser \
                      -Ddb.pass=testpass
                    '''
                }
            }
        }
        stage('Build Image') {
            steps {
                container('oc') {
                    dir('target') {
                        script {
                            def bcExists = sh(script: "oc get bc/spring-boot-app -n ${TEST_NS} --no-headers | wc -l", returnStdout: true).trim() != '0'
                            if (!bcExists) {
                                sh "oc new-build --name=spring-boot-app --binary --strategy=source --image=registry.access.redhat.com/ubi8/openjdk-17:latest --to=${IMAGE_NAME} -n ${TEST_NS}"
                            }
                            def buildOutput = sh(script: "oc start-build spring-boot-app --from-dir=. --wait --output=name -n ${TEST_NS}", returnStdout: true).trim()
                            def buildName = buildOutput ?: "spring-boot-app-${env.BUILD_NUMBER}"
                            sh "oc logs -f ${buildName} -n ${TEST_NS}"
                        }
                    }
                }
            }
        }
        stage('Deploy to Test') {
            steps {
                container('oc') {
                    script {
                        writeFile file: 'dc-test.yaml', text: """
                        apiVersion: apps.openshift.io/v1
                        kind: DeploymentConfig
                        metadata:
                          name: spring-boot-app
                          namespace: ${TEST_NS}
                        spec:
                          replicas: 1
                          selector:
                            app: spring-boot-app
                          triggers:
                          - type: ImageChange
                            imageChangeParams:
                              automatic: true
                              containerNames:
                              - spring-boot-app
                              from:
                                kind: ImageStreamTag
                                name: ${IMAGE_NAME}
                          template:
                            metadata:
                              labels:
                                app: spring-boot-app
                            spec:
                              containers:
                              - name: spring-boot-app
                                image: ${IMAGE_NAME}
                                ports:
                                - containerPort: 8080
                                readinessProbe:
                                  httpGet:
                                    path: /actuator/health
                                    port: 8080
                                  initialDelaySeconds: 10
                                  periodSeconds: 5
                        """
                        sh "oc apply -f dc-test.yaml -n ${TEST_NS}"
                        sh "oc rollout status dc/spring-boot-app -n ${TEST_NS}"
                        sh "oc expose svc/spring-boot-app --name=test-route -n ${TEST_NS} || true"
                    }
                }
            }
        }
    }
    post {
        always {
            archiveArtifacts 'target/*.jar'
        }
    }
}
