package main

import (
	"fmt"
	"microsms/models"
	"microsms/routes"
	"net/http"
	"time"

	"github.com/gin-gonic/gin"
)

var server *gin.Engine
var startTime time.Time

func main() {
	var err error
	db, err := models.InitDB()
	fmt.Println("DB Initialized correctly:", db)
	if err != nil {
		panic("FAILED TO START DB")
	}

	server = gin.Default()
	// converts into a single slash (/) when trying to match a route.
	server.RemoveExtraSlash = true

	// Optional: This handles cases where a user visits /health/ and you defined /health
	server.RedirectTrailingSlash = true
	startTime = time.Now()
	setupRoutes()

	server.Run(":8080") // start server on port 8080

}

func GetHealth(c *gin.Context) {
	uptime := time.Since(startTime)
	c.JSON(http.StatusOK, gin.H{"message": fmt.Sprintf("API Healthy for %s", uptime)})
}

func setupRoutes() {
	apiGroup := server.Group("/api/v0")
	{
		apiGroup.POST("/create", routes.CreateSMSRequest)
		apiGroup.GET("/health", GetHealth)
		apiGroup.GET("/smsrequest", routes.GetSMSRequest)
		apiGroup.GET("/ready", routes.GetReadyToSendSMS)
		apiGroup.PATCH("/smsrequest", routes.UpdateSMSRequest)
	}

}
