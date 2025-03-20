pipeline {
    agent {
        kubernetes {
            label 'spring-agent'
            yaml '''
            apiVersion: v1
            kind: Pod
            spec:
              containers:
              - name: maven
                image: maven:3.8.5-openjdk-17
                command:
                - /bin/sh
                - -c
                - "sleep infinity"
                workingDir: /home/jenkins/agent
                env:
                - name: HOME
                  value: /home/jenkins/agent
                volumeMounts:
                - name: workspace-volume
                  mountPath: /home/jenkins/agent
              - name: oc
                image: quay.io/openshift/origin-cli:4.15
                command:
                - /bin/sh
                - -c
                - "sleep infinity"
                workingDir: /home/jenkins/agent
                volumeMounts:
                - name: workspace-volume
                  mountPath: /home/jenkins/agent
              volumes:
              - name: workspace-volume
                emptyDir: {}
            '''
        }
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
                    sh 'mvn -B -DskipTests clean package'
                }
            }
        }
        stage('Build Image') {
            steps {
                container('oc') {
                    dir('target') {
                        script {
                            openshift.withCluster() {
                                openshift.withProject('test') {
                                    def bcExists = openshift.selector('buildconfig/spring-boot-app').exists()
                                    def build
                                    if (!bcExists) {
                                        build = openshift.newBuild(
                                            "--name=spring-boot-app",
                                            "--binary",
                                            "--strategy=source",
                                            "--image=registry.access.redhat.com/ubi8/openjdk-17:latest",
                                            "--to=spring-boot-app:latest"
                                        )
                                    } else {
                                        build = openshift.startBuild('spring-boot-app', '--from-dir=.')
                                    }
                                    // Get the build name and tail logs manually
                                    def buildName = build.name()
                                    sh "oc logs -f ${buildName}"
                                }
                            }
                        }
                    }
                }
            }
        }
        stage('Deploy') {
            steps {
                container('oc') {
                    script {
                        openshift.withCluster() {
                            openshift.withProject('test') {
                                def dc = openshift.apply(
                                    openshift.raw(
                                        "kind": "DeploymentConfig",
                                        "apiVersion": "v1",
                                        "metadata": ["name": "spring-boot-app"],
                                        "spec": [
                                            "replicas": 1,
                                            "selector": ["app": "spring-boot-app"],
                                            "triggers": [[
                                                "type": "ImageChange",
                                                "imageChangeParams": [
                                                    "automatic": true,
                                                    "containerNames": ["spring-boot-app"],
                                                    "from": ["kind": "ImageStreamTag", "name": "spring-boot-app:latest"]
                                                ]
                                            ]],
                                            "template": [
                                                "metadata": ["labels": ["app": "spring-boot-app"]],
                                                "spec": [
                                                    "containers": [[
                                                        "name": "spring-boot-app",
                                                        "image": "spring-boot-app:latest",
                                                        "ports": [["containerPort": 8080, "protocol": "TCP"]]
                                                    ]]
                                                ]
                                            ]
                                        ]
                                    )
                                )
                                dc.rollout().latest()
                                dc.rollout().status()
                            }
                        }
                    }
                }
            }
        }
    }
}
