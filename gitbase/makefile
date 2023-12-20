install:	
	gradlew clean build 
	docker-compose build

deploy:	
	docker compose up -d

undeploy:
	docker compose down --remove-orphans
	docker image prune -a -f
	
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