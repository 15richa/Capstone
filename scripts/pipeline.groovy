pipeline{
    agent{
        node{
            label 'prod'
        }
    }
    
    environment{
        docker_hub_token='3d19a5a2-0b60-4519-ac9a-35c915e6ecdb'
    }
    
    stages{
        stage("Setup"){
            steps{
                echo "List all environment variables"
                
                sh "printenv | sort"
                
            }
        }
        
        stage("Checkout"){
            steps{
                echo "Fetching code from repository"
                
                cleanWs();
                
                println ""
                println "-----------------------------------------------------"
                println "STAGE: CHECKOUT"
                println "-----------------------------------------------------"
                println ""
                
                echo "GIT_BRANCH = ${env.GIT_BRANCH}"
                echo "BUILD_NUMBER = ${BUILD_NUMBER}"
                
                script{
                    checkout(
                        [
                            $class: 'GitSCM', 
                            branches: [
                                [name: '*/master'] 
                            ], 
                            extensions: [], 
                            userRemoteConfigs: [[
                                credentialsId: 'jenkins-integration', 
                                url: 'https://github.com/15richa/flask-sample.git'
                                ]]
                        ]
                    )

                    sh 'git name-rev --name-only HEAD | cut -d / -f3 > GIT_BRANCH'
                    sh 'cat GIT_BRANCH'
                    git_branch = readFile('GIT_BRANCH').trim()
                    env.GIT_BRANCH = git_branch
                }
                
                println ""
                println "GLOBAL PROPERTIES"
                println "--------------------------------------------------------"
                println "GIT_BRANCH: ${env.GIT_BRANCH}"
                println "--------------------------------------------------------"                
            }
        }

        stage("Build Docker"){
            when{
                environment name: "GIT_BRANCH", value: "master"
            }
            steps{
                echo "Build & publish to prod server"
                script{
                    prod_publish_log = sh (returnStdout: true, script: '''#!/bin/bash

    #docker-hub login
    echo "${docker_hub_token}" | docker login --username richasrivastava15 --password-stdin

    #build project
    docker build --no-cache . -t richasrivastava15/flask-sample:1.1.${BUILD_NUMBER}

    #publish to prod
    docker push richasrivastava15/flask-sample:1.1.${BUILD_NUMBER}

    #Here goes the logic to deploy to Kubernetes
    
    ##Access application
    ##curl http://127.0.0.1:82/
    ''')

                    echo "$prod_publish_log"
                }
            }
        }
    
    stage("Deploy to Kubernetes"){
      steps{
        echo "Connect to Kubernetes and deploy latest docker container"
        script{
          k8s_deploy = sh (returnStdout: true, script: '''#!/bin/bash
          
  export KUBECONFIG="/home/ubuntu/.kube/config"
  
  echo "Check any existing POD running for flask-sample application"
  kubectl get pods -n default|grep -i "flask-sample"
  
  echo "Update the K8s deployment resource with latest push docker image"
  kubectl set image deployment.v1.apps/flask-sample flask-sample=docker.io/richasrivastava15/flask-sample:1.1.${BUILD_NUMBER}
  
  echo "Verify POD is coming up"
  pod_status="Pending"
  while [ "${pod_status}" != "Running" ]
  do
      pod_status=`kubectl get pods -n default|grep -i "flask-sample"|tr -s " "|cut -d " " -f3`
      echo "Pod status = ${pod_status}"
  done
  
  kubectl get pods -n default|grep -i "flask-sample"
  ''')
  
          echo "$k8s_deploy"
        }
      }
    }
  
    stage("Test app"){
      steps{
        echo "Test Application"
        script{
          test_app = sh (returnStdout: true, script: '''#!/bin/bash
  export KUBECONFIG="/home/ubuntu/.kube/config"
  
  #echo "Fetch clusterIP of flask-sample service"
  #serviceIP=`kubectl get service/flask-sample-service -o jsonpath='{.spec.clusterIP}'`
  
  #if [ "${serviceIP}" != "" ]
  #then
  # echo "flask-sample service is running on clusterIP ${serviceIP}"
  # echo "Testing application using curl"
    curl http://44.193.151.106:31276/
  #else
    #echo "Issue accessing application"
  #fi
  ''')
          echo "$test_app"
        }
      }
    }
  }
}
