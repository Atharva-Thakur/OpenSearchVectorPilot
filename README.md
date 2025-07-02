# OpenSearchVectorPilot

## reload the kernel parameters using sysctl
`sudo sysctl -p`

## run the docker-compose file
`docker compose up`

## running the crud app
- `cd open-search-vector-pilot`
- `mvn clean compile`
- `mvn exec:java -Dexec.mainClass="com.example.app.App"`

## getting the dataset
- gdown "https://drive.google.com/uc?id=1KD-v9vKoJRjiRr6w3zrcDMmebrEtXCeu"
- gdown "https://drive.google.com/uc?id=1l5-HIdGgmlieAlzPxgEbdXX4ZpbbrQnX"

## sample curl commands
- curl -G "http://localhost:8080/api/books/search" --data-urlencode "field=title" --data-urlencode "value=Harry Potter"
- curl -G "http://localhost:8080/api/books/vector-search" --data-urlencode "query=Hobbit"