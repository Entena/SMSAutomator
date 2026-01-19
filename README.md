## What IS This
MicroSms is a stupid idea I have, create a webservice that creates SMS requests and partner it with a spare Android phone/SIM. The Phone will then query the server and grab any pending requests (one by one by visiting a URL), then it will autocreate an SMS using the built in Android API. This Phone will run a custom built Android App that will do this on a timer.

### So what is THIS
This is the webserver, it's going to support the following scenarios

#### Send A SMS
1. Users will hit an API path with a body containing the message to send and a destination phone number
2. Upon successful creation return the GUID of the MessageRequest
    * Do this to later support flexible payment (Venmo/Paypal requests)
3. MessageRequest is now marked *PAYMENT_NEEDED*, user completes payment and auto job changes status, for now though we will just use a /messages/<guid>/update API to update the status, MessageRequest leaves as *READY_TO_SEND*
4. Phone queries /tosend which returns the earliest *READY_TO_SEND* record
5. Phone captures top record info and then registers the message as *TAKEN* via /messages/<guid>/update
6. Phone sends SMS and then registeres message as *COMPLETE*
