pipeline {
    agent none
    stages {
        stage('Build JAR') { 
           agent {
                docker {
                    image 'leiningen' 
                    args '-v /srv/docker/var/m2:/root/.m2'
                }
            }
            steps {
                sh 'lein do clean, uberjar'
            }
        }
        stage('Build image') {
            agent any
            steps {
                script {
                    docker.withServer('') {
                    docker.withRegistry('https://registry.i.hosaka.io') {
                        def app = docker.build("registry.i.hosaka.io/oscillator")
                        app.push("latest")
                        app.push("${env.BRANCH_NAME}")
                    }}
                }
            }
        }
    }
}