# Local Database Testing Report

Tester: Yismaw Tilaye  
Branch: feature/offline-mode-ui-yismaw  

---

## Manual Functional Test Cases

TC_APP_START — PASS  
Application started successfully. Backend and frontend launched without errors.

TC_LOGIN — PASS  
User authentication worked correctly and dashboard loaded successfully.

TC_OFFLINE_TOGGLE — PASS  
Offline persistence toggle enabled successfully.

TC_OFFLINE_TOGGLE — PASS  
Offline persistence toggle enabled successfully from the Settings page.

TC_DATA_SAVE_ONLINE — PASS  
Data saved correctly while online. A test post was created and displayed in the feed.

TC_DATA_SAVE_OFFLINE — PASS  
Network connection was disabled on the emulator. A new post was created and stored locally while offline.

TC_LOCAL_PERSISTENCE — PASS  
After restarting the application, both the online and offline posts remained visible, confirming local database persistence.

TC_SYNC_AFTER_RECONNECT — PASS  
After reconnecting the network, the application continued functioning normally and previously created posts remained accessible.

TC_ERROR_MONITOR — PASS  
Frontend and backend terminals were monitored during testing. Only normal debug logs were observed and no runtime errors, exceptions, or crashes occurred.

---

## Automated Local Database Tests

Location:
frontend/test/local_db/

---

## Automated Test Execution Result

The automated tests were executed using the Flutter testing framework.

Command used:


Result:

All tests executed successfully.

### Test Output Screenshot

![Local DB Tests Passed](local_db_tests_passed.png)