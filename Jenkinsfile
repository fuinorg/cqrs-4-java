pipeline {
    agent any 
    tools { 
        jdk 'OpenJDK 11 (latest)'
    }
    stages {
        stage ('Initialize') {
            steps {
                sh "./mvnw -version"
            }
        }
        stage('Build') { 
            steps {
                sh "./mvnw clean javadoc:jar deploy -U -B -P sonatype-oss-release -s /private/jenkins/settings.xml"
            }
        }
    }
}
