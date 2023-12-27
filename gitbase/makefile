install:	
	gradlew clean build 

deploy:
	docker-compose build	
	docker-compose up -d
	
scale-deploy:
	docker-compose build	
	docker-compose up -d --scale "$s"	

deploy-rabMQ:	
	docker-compose -f docker-compose-partitions.yml build	
	docker-compose -f docker-compose-partitions.yml up -d

deploy-kafka:	
	docker-compose -f docker-compose-kafka.yml build	
	docker-compose -f docker-compose-kafka.yml up -d


undeploy:
	docker-compose down --remove-orphans

undeploy-rabMQ:
	docker-compose -f docker-compose-partitions.yml down --remove-orphans
	
undeploy-kafka:	
	docker-compose -f docker-compose-kafka.yml down --remove-orphans	
	
	
clean-docker:
	docker image prune -a -f	

git:
	git pull
	git status
	git add .
	git commit -m "$m"
	git push
	git status

	
status-ms:
	docker-compose ps
	
redeploy:
	docker compose down --remove-orphans
	docker image prune -a -f
	gradlew clean build 
	docker-compose build
	docker compose up -d