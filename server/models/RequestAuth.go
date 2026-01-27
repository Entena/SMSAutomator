package models

import (
	"microsms/constants"

	"github.com/google/uuid"
	"gorm.io/gorm"
)

/*
1. Create message, register phone, SMSSender is now marked as TO_VERIFY
*/
//type OptInStatus string
//
//const (
//TRUE  OptInStatus = "true"
//FALSE OptInStatus = "false"
//ASK   OptInStatus = "ask"
//)

type OptIn struct {
	ID      uuid.UUID             `json:"id" gorm:"primary_key"`
	Number  string                `json:"number" gorm:"uniqueIndex"`
	Status  constants.OptInStatus `json:"contact" gorm:"not null"` // true means they opted in
	Created int64                 `json:"created" gorm:"autoCreateTime"`
	Updated int64                 `json:"updated" gorm:"autoUpdateTime"`
}

// In order to send a message you must first register a sender number, then you'll receive an API Key
// In order to send a mssage we will first send an auth, then respond with your API key (valid for one approval)
// This requestauth is going to be used in an external API for verifying requests
type RequestAuth struct {
	ID        uuid.UUID                   `json:"id" gorm:"primary_key"`
	RequestID uuid.UUID                   `json:"request_id" gorm:"not null"`
	From      string                      `json:"from" gorm:"not null;unique"`
	To        string                      `json:"to" gorm:"not null"`
	APIKey    string                      `json:"api_key"  gorm:"not null;unique"`
	Status    constants.RequestAuthStatus `json:"status"`
	Created   int64                       `json:"created" gorm:"autoCreateTime"`
	Updated   int64                       `json:"updated" gorm:"autoUpdateTime"`
}

/*
*
We assume that the input data to request auth will be validated. This means use the Number checkers and pass a formatted
string in.
*
*/
func (auth *RequestAuth) BeforeCreate(tx *gorm.DB) error {
	auth.ID = uuid.New()
	// Make sure we aren't creating a naughty boi request
	var fromOptIn OptIn
	var toOptIn OptIn
	tx.First(&fromOptIn, &OptIn{Number: auth.From})
	switch tx.RowsAffected { // Create records if they don't exist
	case 0:
		tx.First(&fromOptIn, &OptIn{Number: auth.From, Status: constants.OptInStatus_TRUE})
		if tx.RowsAffected == 0 {
			fromOptIn = OptIn{
				Number: auth.From,
				Status: constants.OptInStatus_ASK,
			}
			tx.Create(&fromOptIn)
		}
	default:
		break
	}

	tx.First(&toOptIn, &OptIn{Number: auth.To})
	switch tx.RowsAffected {
	case 0:
		tx.First(&toOptIn, &OptIn{Number: auth.To, Status: constants.OptInStatus_TRUE})
		if tx.RowsAffected == 0 {
			toOptIn = OptIn{
				Number: auth.To,
				Status: constants.OptInStatus_ASK,
			}
			tx.Create(&toOptIn)
		}
	default:
		break
	}
	// if either side opted out, then deny
	if fromOptIn.Status == constants.OptInStatus_FALSE || toOptIn.Status == constants.OptInStatus_FALSE {
		auth.Status = constants.RequestAuthStatus_DENIED
	} else
	// if both sides are verified then approve
	if fromOptIn.Status == constants.OptInStatus_TRUE || toOptIn.Status == constants.OptInStatus_TRUE {
		auth.Status = constants.RequestAuthStatus_READY
	} else {
		auth.Status = constants.RequestAuthStatus_TO_VERIFY
	}
	return nil

}

// Get the opt in record for a phone
func GetOptIn(phone string) (*OptIn, error) {
	var optin OptIn

	err := DB.First(&optin, &OptIn{Number: phone}).Error
	if err != nil {
		return nil, err
	}
	return &optin, nil
}

func UpdateOptIn(phone string, status constants.OptInStatus) (*OptIn, error) {
	optin, err := GetOptIn(phone)
	if err != nil {
		return nil, err
	}

	optin.Status = status
	err = DB.Save(optin).Error
	if err != nil {
		return nil, err
	}
	return optin, nil
}

// Get the earliest opt in we haven't asked yet
func GetEarliestOptIn() (*OptIn, error) {
	var earliest OptIn
	result := DB.Model(&OptIn{}).Where(&OptIn{Status: constants.OptInStatus_ASK}).Order("created asc").Limit(1).First(&earliest)
	if result.Error != nil {
		return nil, result.Error
	}
	return &earliest, nil
}
