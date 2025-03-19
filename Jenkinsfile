pipeline {
    agent {
        label 'maven'  // Uses a Maven-enabled agent pod
    }
    stages {
        stage('Checkout') {
            steps {
                git url: 'https://github.com/TuDaoLW/test-app1.git', branch: 'master'
            }
        }
        stage('Build') {
            steps {
                sh 'mvn -B -DskipTests clean package'  // Build the JAR
            }
        }
        stage('Build Image and Deploy') {
            steps {
                script {
                    openshift.withCluster() {
                        openshift.withProject('spring-app-ns') {
                            // Build the image using the JAR
                            def buildConfig = openshift.newBuild(
                                "--name=spring-boot-app",
                                "--binary",
                                "--strategy=source",
                                "--from-dir=complete/target",
                                "--to=spring-boot-app:latest"
                            )
                            buildConfig.logs('-f')  // Follow build logs

                            // Deploy the app
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
                            dc.rollout().status()  // Wait for rollout
                        }
                    }
                }
            }
        }
    }
}
