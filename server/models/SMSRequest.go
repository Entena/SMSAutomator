package models

import (
	"errors"
	"fmt"
	"microsms/constants"

	"github.com/google/uuid"
	"gorm.io/driver/sqlite"
	"gorm.io/gorm"
)

var DB *gorm.DB // Global DB pointer

// SMSRequest definition
type SMSRequest struct {
	ID         uuid.UUID               `json:"id" gorm:"primary_key"`
	ToNumber   string                  `json:"to_number" gorm:"not null"`
	FromNumber string                  `json:"from_number" gorm:"not null"`
	Status     constants.RequestStatus `json:"status"`
	Message    string                  `json:"message"`
	Created    int64                   `json:"created" gorm:"autoCreateTime"`
}

// This should never happen, but hey if it does we can at least log something
func (smsrequest *SMSRequest) ToNumberF() string {
	f_phone, err := constants.GetPhone(smsrequest.ToNumber)
	if err != nil {
		fmt.Printf("ERROR COULD NOT RETURN A FORMATTED TO PHONE NUMBER!!! THIS REQUIRES MANUAL REMEDIATION")
		return smsrequest.ToNumber
	}
	return f_phone
}

func (smsrequest *SMSRequest) FromNumberF() string {
	f_phone, err := constants.GetPhone(smsrequest.ToNumber)
	if err != nil {
		fmt.Printf("ERROR COULD NOT RETURN A FORMATTED TO PHONE NUMBER!!! THIS REQUIRES MANUAL REMEDIATION")
		return smsrequest.ToNumber
	}
	return f_phone
}

// To String my struct
func (smsrequest SMSRequest) String() string {
	return fmt.Sprintf("SMSRequest{ ID: %s, Status: %s, Message: %s}", smsrequest.ID, smsrequest.Status, smsrequest.Message)
}

// Good old precreate hook to populate the id
func (smsrequest *SMSRequest) BeforeCreate(tx *gorm.DB) error {
	smsrequest.ID = uuid.New()
	// First we need to check that the numbers are ok
	smsrequest.Status = constants.RequestStatus_VERIFY_CHECK
	// Create the RequestAuth entry, fail in same txn if either can't be made
	requestAuth := RequestAuth{
		RequestID: smsrequest.ID,            // dupe data because each request is a session to contact
		From:      smsrequest.FromNumberF(), //Get formatted data for request auth
		To:        smsrequest.ToNumberF(),
	}
	err := DB.Create(&requestAuth).Error
	if err != nil {
		return err
	}
	// Check our request auth status to see if we are naughty
	// cascade our status up
	if requestAuth.Status == constants.RequestAuthStatus_DENIED {
		smsrequest.Status = constants.RequestStatus_BLOCKED
	} else if requestAuth.Status == constants.RequestAuthStatus_READY {
		smsrequest.Status = constants.RequestStatus_READY_TO_SEND
	} else {
		smsrequest.Status = constants.RequestStatus_VERIFY_CHECK
	}

	return nil
}

// Method to create new SMSRequest
func CreateSMSRequest(smsrequest *SMSRequest) error {
	if smsrequest.Message == "" {
		return fmt.Errorf("Error invalid message %s", smsrequest.Message)
	}
	if !constants.IsValidPhone((smsrequest.ToNumber)) {
		return fmt.Errorf("Error invalid to phone number %s", smsrequest.ToNumber)
	}
	if !constants.IsValidPhone(smsrequest.FromNumber) { // Get the raw numbers on purpose
		return fmt.Errorf("Error invalid from phone number %s", smsrequest.FromNumber)
	}
	result := DB.Create(smsrequest)
	if result.Error != nil {
		fmt.Println("Error creating SMS Request:", result.Error)
		return result.Error
	}
	fmt.Println("Create new SMS Request: ", smsrequest)
	return nil
}

// Update SMSRequest with new status
func UpdateSMSRequest(id string, newStatus constants.RequestStatus) (*SMSRequest, error) {
	if !constants.IsValidRequestStatus(string(newStatus)) {
		return nil, fmt.Errorf("Error invalid status %s", newStatus)
	}
	smsrequest, err := GetSMSRequest(id)
	if err != nil {
		fmt.Printf("ERROR UPDATING SMSREQUEST %s, %s", id, err)
		return nil, err
	}
	if smsrequest == nil {
		fmt.Printf("ERROR COULD NOT FIND SMSREQUEST TO UPDATE %s", id)
		return nil, errors.New("COULD NOT FIND RECORD")
	}
	smsrequest.Status = newStatus
	err = DB.Save(smsrequest).Error
	if err != nil {
		fmt.Printf("ERROR SAVING UPDATE TO DB %s", err)
		return nil, err
	}
	return smsrequest, nil
}

// Get the single SMSRequest or return nil
func GetSMSRequest(id string) (*SMSRequest, error) {
	fmt.Printf("GET SMSREQUEST BY ID %s\n", id)
	smsrequest := SMSRequest{}
	uid := uuid.MustParse(id)
	result := DB.First(&smsrequest, uid)
	if result.Error != nil {
		fmt.Printf("ERROR FINDING SMSREQUEST %s\n", result.Error)
		return nil, result.Error
	}
	return &smsrequest, nil
}

// Get the earliest ready_to_send record
func GetEarliestSMSRequest() (*SMSRequest, error) {
	fmt.Printf("GET EARLIEST SMSREQUEST")
	var earliest SMSRequest
	result := DB.Model(&SMSRequest{}).Where(&SMSRequest{Status: constants.RequestStatus_READY_TO_SEND}).Order("created ASC").First(&earliest)
	if result.Error != nil {
		fmt.Printf("Error finding ready to send SMS")
	}
	return &earliest, nil
}

// Create DB connection
// TODO use psql in the future and set up support for it
func InitDB(dbPath string) (*gorm.DB, error) {
	fmt.Printf("Initing DB with dbPath %s\n", dbPath)
	db, err := gorm.Open(sqlite.Open(dbPath), &gorm.Config{})
	if err != nil {
		return nil, err
	}
	DB = db
	DB.AutoMigrate(&SMSRequest{}) // create the reqeuest table
	DB.AutoMigrate(&RequestAuth{})
	DB.AutoMigrate(&OptIn{})
	fmt.Println("DB Inited")
	return DB, nil
}
