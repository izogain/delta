# Continuous Delivery at Flow

- Our "Ionroller"
- Knowledge of apps
- Knowledge of master ahead of latest tag
- Data on what's running in production
- Decide what we do for traffic management w/ data
- Dockerize all our services (api, www, and postgresql)
- Use AWS EC2 Container Service (ECS) to automate deployments to AWS environment
- Tooling to be built to eliminate need to go into AWS console

##### Mike:

brain.flow.io/user
  tag     docker image
  0.0.5   <not yet available>
  0.0.4   flowcommerce/user:0.0.4

master: 0.0.5-a1234cba

- one actor per project
- periodically poll github to look for tags
- simple webhook ala dependency to trigger poll to look for tags and pointer for master
- periodically poll docker hub to look for images
- simple webhook to trigger poll docker hub to look for images

##### Paolo:

ECS:

  ELB [static]

  cluster   service   task  image_id
  xxx                       flowcommerce/user:0.0.5
  xxx                       flowcommerce/user:0.0.5
  xxx                       flowcommerce/user:0.0.4

  - message: Deploy("flowcommerce/user:0.0.5")

  brain.flow.io/
  paolo: flowcommerce/user     0.0.4:67%     0.0.5:33%
    flowcommerce/user has commits on master that are not yet tagged
    flowcommerce/user:0.0.5 deploy is in process
    flowcommerce/fulfilliment:0.0.2 waiting on docker image

### General Steps for Continuous Deployment

- Development environment
  - Make necessary updates, create a PR, merge to master
  - Create new tag - Manual for now, but figure out where this lives
- Docker Hub
  - Automated build picks up new tag and builds new image version
  - Web hook back to Brain is triggered (See below for sample JSON returned from Docker Hub)
- Brain
  - For new services/One-time setup
    - Create security group for service with HTTP/SSH and custom TCP rule for port in registry
    - Create EC2 ELB for the service
      - Will be used by the auto-scaling group and the ECS service
      - Listeners should be based on registry port information  
    - EC2
      - Create launch configurations
        - Use custom service security group from above and services security group for all services
        - "User Data" should be fill with ECS env vars (See below for sample launch config User Data)
      - Create auto-scaling group (ASG) with launch configuration
    - ECS
      - Create cluster
  - Things to do in AWS for NEW tag
    - ECS
      - Create NEW task definition revision (See below for sample task definition JSON)
      - Create NEW service in cluster with NEW task definition and existing ELB
        - Set desired count to 0
  - Switching traffic from OLD to NEW tag
    - See: http://docs.aws.amazon.com/AmazonECS/latest/developerguide/update-service.html
    - Scale up NEW service desired count and scale down OLD service desired count

### Open Questions

- We don't have explicit control over what image tags are run in which specific EC2 instances. Do we need finer-grain control?
- ...

### Sample Docker Hub Web Hook

```
{
  "push_data":{
    "pushed_at":1454425945,
    "images":[
		....
    ],
    "tag":"0.0.4",
    "pusher":"flowcommerce"
  },
  "callback_url":"https://registry.hub.docker.com/u/flowcommerce/fulfillment/hook/2fij20bc20ce44d2cf140ej0cfeci01f5/",
  "repository":{
    "status":"Active",
    "description":"Flow Commerce Fulfillment API",
    "is_trusted":true,
    "full_description":"Fulfillment\n========\n\nThings related to fulfillment:\n- carriers and service levels\n- centers (fulfillment centers, stores, etc)\n\n",
    "repo_url":"https://registry.hub.docker.com/u/flowcommerce/fulfillment/",
    "owner":"flowcommerce",
    "is_official":false,
    "is_private":true,
    "name":"fulfillment",
    "namespace":"flowcommerce",
    "star_count":0,
    "comment_count":0,
    "date_created":1451306252,
    "dockerfile":"FROM flowcommerce/play:0.0.7\n\nADD . /opt/play\n\nWORKDIR /opt/play\n\nRUN sbt clean stage\n\nENTRYPOINT [\"java\", \"-jar\", \"/root/environment-provider.jar\", \"run\", \"play\", \"fulfillment\", \"api/target/universal/stage/bin/fulfillment-api\"]\n",
    "repo_name":"flowcommerce/fulfillment"
  }
}
```

### Sample Launch Configuration User Data

Get ECS_ENGINE_AUTH_DATA /web/keys/services/brain-docker-hub-user.txt

```
#!/bin/bash
echo 'ECS_CLUSTER=splashpage-api-ecs-cluster' >> /etc/ecs/ecs.config
echo 'ECS_ENGINE_AUTH_TYPE=dockercfg' >> /etc/ecs/ecs.config
echo 'ECS_ENGINE_AUTH_DATA={"https://index.docker.io/v1/":{"auth":"xxxxxyyyyyzzzzzz","email":"xxxxxx@email.com"}}' >> /etc/ecs/ecs.config
```

### Sample ECS Task Definition

```
{
  "containerDefinitions":[
    {
      "volumesFrom":[],
      "memory":500,
      "extraHosts":null,
      "dnsServers":null,
      "disableNetworking":null,
      "dnsSearchDomains":null,
      "portMappings":[
        {
          "hostPort":6041,
          "containerPort":9000,
          "protocol":"tcp"
        }
      ],
      "hostname":null,
      "essential":true,
      "entryPoint":null,
      "mountPoints":[],
      "name":"splashpage-api-ecs-container",
      "ulimits":null,
      "dockerSecurityOptions":null,
      "environment":[],
      "links":null,
      "workingDirectory":null,
      "readonlyRootFilesystem":null,
      "image":"flowcommerce/splashpage:0.1.15",
      "command":[
        "production"
      ],
      "user":null,
      "dockerLabels":null,
      "logConfiguration":null,
      "cpu":0,
      "privileged":null
    }
  ],
  "volumes":[],
  "family":"splashpage-api-ecs-task"
}
```
