String tfProject      = "nia"
String tfEnvironment  = "build1"
String tfComponent    = "gp2gp"
String redirectEnv    = "build1"          // Name of environment where TF deployment needs to be re-directed
String redirectBranch = "main"      // When deploying branch name matches, TF deployment gets redirected to environment defined in variable "redirectEnv"
Boolean publishWiremockImage = true // true: To publish gp2gp wiremock image to AWS ECR gp2gp-wiremock
Boolean publishMhsMockImage  = true // true: to publsh mhs mock image to AWS ECR gp2gp-mock-mhs
Boolean gpccDeploy    = true         // true: To deploy GPC-Consumer service inside gp2gp
String gpccBranch     = "main"      // Name of branch as a prefix to image name (GPC-Consumer) stored in ECR
String gpccEcrRepo    = "gpc-consumer" // ECR Repo Name of GPC-Consumer
String tfGpccImagePrefix  = "gpc-consumer" // Image name prefix of GPC-Consumer

pipeline {
    agent{
        label 'jenkins-workers'
    }

    options {
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: "10"))
    }

    environment {
        BUILD_TAG = sh label: 'Generating build tag', returnStdout: true, script: 'python3 scripts/tag.py ${GIT_BRANCH} ${BUILD_NUMBER} ${GIT_COMMIT}'
        ECR_REPO_DIR = "gp2gp"
        WIREMOCK_ECR_REPO_DIR = "gp2gp-wiremock"
        MHS_MOCK_ECR_REPO_DIR = "gp2gp-mock-mhs"
        DOCKER_IMAGE = "${DOCKER_REGISTRY}/${ECR_REPO_DIR}:${BUILD_TAG}"
        WIREMOCK_DOCKER_IMAGE = "${DOCKER_REGISTRY}/${WIREMOCK_ECR_REPO_DIR}:${BUILD_TAG}"
        MHS_MOCK_DOCKER_IMAGE  = "${DOCKER_REGISTRY}/${MHS_MOCK_ECR_REPO_DIR}:${BUILD_TAG}"
    }

    stages {
        stage('Build') {
            stages {
                stage('Tests') {
                    steps {
                        script {
                            sh '''
                                source docker/vars.local.sh
                                docker-compose -f docker/docker-compose.yml -f docker/docker-compose-tests.yml build
                                docker-compose -f docker/docker-compose.yml -f docker/docker-compose-tests.yml up --exit-code-from gp2gp
                            '''
                        }
                    }
                    post {
                        always {
                            sh "docker cp tests:/home/gradle/service/build ."
                            archiveArtifacts artifacts: 'build/reports/**/*.*', fingerprint: true
                            junit '**/build/test-results/**/*.xml'
                            recordIssues(
                                enabledForFailure: true,
                                tools: [
                                    checkStyle(pattern: 'build/reports/checkstyle/*.xml'),
                                    spotBugs(pattern: 'build/reports/spotbugs/*.xml')
                                ]
                            )
                            step([
                                $class : 'JacocoPublisher',
                                execPattern : '**/build/jacoco/*.exec',
                                classPattern : '**/build/classes/java',
                                sourcePattern : 'src/main/java',
                                exclusionPattern : '**/*Test.class'
                            ])
                            sh "rm -rf build"
                            sh "docker-compose -f docker/docker-compose.yml -f docker/docker-compose-tests.yml down"
                        }
                    }
                }

                stage('Build Docker Images') {
                    steps {
                        script {
                            if (sh(label: 'Running gp2gp docker build', script: 'docker build -f docker/service/Dockerfile -t ${DOCKER_IMAGE} .', returnStatus: true) != 0) {error("Failed to build gp2gp Docker image")}
                            if (publishWiremockImage) {
                                if (sh(label: 'Running gp2gp-wiremock docker build', script: 'docker build -f docker/wiremock/Dockerfile -t ${WIREMOCK_DOCKER_IMAGE} docker/wiremock', returnStatus: true) != 0) {error("Failed to build gp2gp-wiremock Docker image")}
                            }
                            if (publishMhsMockImage) {
                                if (sh(label: 'Running gp2gp-mhs-mock docker build', script: 'docker build -f docker/mock-mhs-adaptor/Dockerfile -t ${MHS_MOCK_DOCKER_IMAGE} .', returnStatus: true) != 0) {error("Failed to build gp2gp-mock-mhs Docker image")}
                            }
                        }
                    }
                }

                stage('Push Image') {
                    when {
                        expression { currentBuild.resultIsBetterOrEqualTo('SUCCESS') }
                    }
                    steps {
                        script {
                            if (ecrLogin(TF_STATE_BUCKET_REGION) != 0 )  { error("Docker login to ECR failed") }
                            String dockerPushCommand = "docker push ${DOCKER_IMAGE}"
                            if (sh (label: "Pushing image", script: dockerPushCommand, returnStatus: true) !=0) { error("Docker push gp2gp image failed") }

                            String dockerPushCommandWiremock = "docker push ${WIREMOCK_DOCKER_IMAGE}"
                            if (publishWiremockImage) {
                                if (sh (label: "Pushing Wiremock image", script: dockerPushCommandWiremock, returnStatus: true) !=0) { error("Docker push gp2gp-wiremock image failed") }
                            }

                            String dockerPushCommandMhsMock = "docker push ${MHS_MOCK_DOCKER_IMAGE}"
                            if (publishMhsMockImage) {
                                if (sh(label: "Pushing MHS Mock image", script: dockerPushCommandMhsMock, returnStatus: true) != 0) {error("Docker push gp2gp-mock-mhs image failed") }
                            }
                        }
                    }
                }
                stage('Deploy & Test') {
                    options {
                        lock("${tfProject}-${tfEnvironment}-${tfComponent}")
                    }
                    stages {

                        stage('Deploy using Terraform') {
                            steps {
                                script {
                                    
                                    // Check if TF deployment environment needs to be redirected
                                    if (GIT_BRANCH == redirectBranch) { tfEnvironment = redirectEnv }
                                    
                                    String tfCodeBranch  = "develop"
                                    String tfCodeRepo    = "https://github.com/nhsconnect/integration-adaptors"
                                    String tfRegion      = "${TF_STATE_BUCKET_REGION}"
                                    List<String> tfParams = []
                                    Map<String,String> tfVariables = ["${tfComponent}_build_id": BUILD_TAG]
                                      if (gpccDeploy) {
                                          tfVariables.put("${tfGpccImagePrefix}_build_id", getLatestImageTag(gpccBranch, gpccEcrRepo, tfRegion))
                                      }
                                    dir ("integration-adaptors") {
                                      git (branch: tfCodeBranch, url: tfCodeRepo)
                                      dir ("terraform/aws") {
                                        if (terraformInit(TF_STATE_BUCKET, tfProject, tfEnvironment, tfComponent, tfRegion) !=0) { error("Terraform init failed")}
                                        if (terraform('apply', TF_STATE_BUCKET, tfProject, tfEnvironment, tfComponent, tfRegion, tfVariables) !=0 ) { error("Terraform Apply failed")}
                                      }
                                    }
                                }  //script
                            } // steps
                        } // Stage Deploy using Terraform

                        stage('E2E Tests') {
                            steps {
                                sh '''
                                    source docker/vars.local.sh
                                    docker-compose -f docker/docker-compose.yml -f docker/docker-compose-e2e-tests.yml build
                                    docker-compose -f docker/docker-compose.yml -f docker/docker-compose-e2e-tests.yml up --exit-code-from gp2gp-e2e-tests mongodb activemq gp2gp wiremock gpcc gp2gp-e2e-tests
                                '''

                            }
                            post {
                                always {
                                    sh "docker cp e2e-tests:/home/gradle/e2e-tests/build ."
                                    sh "mv build e2e-build"
                                    archiveArtifacts artifacts: 'e2e-build/reports/**/*.*', fingerprint: true
                                    junit '**/e2e-build/test-results/**/*.xml'
                                    sh "rm -rf e2e-build"
                                    sh "docker-compose -f docker/docker-compose.yml -f docker/docker-compose-e2e-tests.yml down"
                                }
                            }
                        } //stage E2E Test
                    } //Stages Deploy & Test
                }  // Stage Deploy & Test
            }  // Stages Build
        } //Stage Build
    }  //Stages
    post {
        always {
            sh label: 'Remove images created by docker-compose', script: 'docker-compose -f docker/docker-compose.yml -f docker/docker-compose-tests.yml down --rmi local'
            sh label: 'Remove exited containers', script: 'docker rm $(docker ps -a -f status=exited -q)'
            sh label: 'Remove images tagged with current BUILD_TAG', script: 'docker image rm -f $(docker images "*/*:*${BUILD_TAG}" -q) $(docker images "*/*/*:*${BUILD_TAG}" -q) || true'
        }
    }
}  // Pipeline

int ecrLogin(String aws_region) {
    String ecrCommand = "aws ecr get-login --region ${aws_region}"
    String dockerLogin = sh (label: "Getting Docker login from ECR", script: ecrCommand, returnStdout: true).replace("-e none","") // some parameters that AWS provides and docker does not recognize
    return sh(label: "Logging in with Docker", script: dockerLogin, returnStatus: true)
}

String tfEnv(String tfEnvRepo="https://github.com/tfutils/tfenv.git", String tfEnvPath="~/.tfenv") {
  sh(label: "Get tfenv" ,  script: "git clone ${tfEnvRepo} ${tfEnvPath}", returnStatus: true)
  sh(label: "Install TF",  script: "${tfEnvPath}/bin/tfenv install"     , returnStatus: true)
  return "${tfEnvPath}/bin/terraform"
}

int terraformInit(String tfStateBucket, String project, String environment, String component, String region) {
  String terraformBinPath = tfEnv()
  println("Terraform Init for Environment: ${environment} Component: ${component} in region: ${region} using bucket: ${tfStateBucket}")
  String command = "${terraformBinPath} init -backend-config='bucket=${tfStateBucket}' -backend-config='region=${region}' -backend-config='key=${project}-${environment}-${component}.tfstate' -input=false -no-color"
  dir("components/${component}") {
    return( sh( label: "Terraform Init", script: command, returnStatus: true))
  } // dir
} // int TerraformInit

int terraform(String action, String tfStateBucket, String project, String environment, String component, String region, Map<String, String> variables=[:], List<String> parameters=[]) {
    println("Running Terraform ${action} in region ${region} with: \n Project: ${project} \n Environment: ${environment} \n Component: ${component}")
    variablesMap = variables
    variablesMap.put('region',region)
    variablesMap.put('project', project)
    variablesMap.put('environment', environment)
    variablesMap.put('tf_state_bucket',tfStateBucket)
    parametersList = parameters
    parametersList.add("-no-color")
    //parametersList.add("-compact-warnings")  /TODO update terraform to have this working

    // Get the secret variables for global
    String secretsFile = "etc/secrets.tfvars"
    writeVariablesToFile(secretsFile,getAllSecretsForEnvironment(environment,"nia",region))
    String terraformBinPath = tfEnv()

    List<String> variableFilesList = [
      "-var-file=../../etc/global.tfvars",
      "-var-file=../../etc/${region}_${environment}.tfvars",
      "-var-file=../../${secretsFile}"
    ]
    if (action == "apply"|| action == "destroy") {parametersList.add("-auto-approve")}
    List<String> variablesList=variablesMap.collect { key, value -> "-var ${key}=${value}" }
    String command = "${terraformBinPath} ${action} ${variableFilesList.join(" ")} ${parametersList.join(" ")} ${variablesList.join(" ")} "
    dir("components/${component}") {
      return sh(label:"Terraform: "+action, script: command, returnStatus: true)
    } // dir
} // int Terraform

Map<String,String> collectTfOutputs(String component) {
  Map<String,String> returnMap = [:]
  dir("components/${component}") {
    String terraformBinPath = tfEnv()
    List<String> outputsList = sh (label: "Listing TF outputs", script: "${terraformBinPath} output", returnStdout: true).split("\n")
    outputsList.each {
      returnMap.put(it.split("=")[0].trim(),it.split("=")[1].trim())
    }
  } // dir
  return returnMap
}

// Retrieving Secrets from AWS Secrets
String getSecretValue(String secretName, String region) {
  String awsCommand = "aws secretsmanager get-secret-value --region ${region} --secret-id ${secretName} --query SecretString --output text"
  return sh(script: awsCommand, returnStdout: true).trim()
}

Map<String,Object> decodeSecretKeyValue(String rawSecret) {
  List<String> secretsSplitted = rawSecret.replace("{","").replace("}","").split(",")
  Map<String,Object> secretsDecoded = [:]
  secretsSplitted.each {
    String key = it.split(":")[0].trim().replace("\"","")
    Object value = it.split(":")[1]
    secretsDecoded.put(key,value)
  }
  return secretsDecoded
}

List<String> getSecretsByPrefix(String prefix, String region) {
  String awsCommand = "aws secretsmanager list-secrets --region ${region} --query SecretList[].Name --output text"
  List<String> awsReturnValue = sh(script: awsCommand, returnStdout: true).split()
  return awsReturnValue.findAll { it.startsWith(prefix) }
}

Map<String,Object> getAllSecretsForEnvironment(String environment, String secretsPrefix, String region) {
  List<String> globalSecrets = getSecretsByPrefix("${secretsPrefix}-global",region)
  println "global secrets:" + globalSecrets
  List<String> environmentSecrets = getSecretsByPrefix("${secretsPrefix}-${environment}",region)
  println "env secrets:" + environmentSecrets
  Map<String,Object> secretsMerged = [:]
  globalSecrets.each {
    String rawSecret = getSecretValue(it,region)
    if (it.contains("-kvp")) {
      secretsMerged << decodeSecretKeyValue(rawSecret)
    } else {
      secretsMerged.put(it.replace("${secretsPrefix}-global-",""),rawSecret)
    }
  }
  environmentSecrets.each {
    String rawSecret = getSecretValue(it,region)
    if (it.contains("-kvp")) {
      secretsMerged << decodeSecretKeyValue(rawSecret)
    } else {
      secretsMerged.put(it.replace("${secretsPrefix}-${environment}-",""),rawSecret)
    }
  }
  return secretsMerged
}

void writeVariablesToFile(String fileName, Map<String,Object> variablesMap) {
  List<String> variablesList=variablesMap.collect { key, value -> "${key} = ${value}" }
  sh (script: "touch ${fileName} && echo '\n' > ${fileName}")
  variablesList.each {
    sh (script: "echo '${it}' >> ${fileName}")
  }
}

// Retrieving Docker images from ECR

String getLatestImageTag(String prefix, String ecrRepo, String region) {
  List<String> imageTags = getAllImageTagsByPrefix(prefix, ecrRepo, region)
  Map<Integer, String> buildNumberTag = [:]
  Integer maxBuild = 0
  imageTags.each {
    Integer currBuild = it.replace("${prefix}-","").split("-")[0]
    buildNumberTag.put(currBuild, it)
    if (currBuild  > maxBuild) { maxBuild = currBuild}
  }
  return buildNumberTag[maxBuild]
}

List<String> getAllImageTagsByPrefix(String prefix, String ecrRepo, String region) {
  String awsCommand = "aws ecr list-images --repository-name ${ecrRepo} --region ${region} --query imageIds[].imageTag --output text"
  List<String> allImageTags = sh (script: awsCommand, returnStdout: true).split()
  return allImageTags.findAll{it.startsWith(prefix)}
}
