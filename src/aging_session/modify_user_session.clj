(ns aging-session.modify-user-session)

(defprotocol SessionLookup
  "A protocol for looking up the session details of a user based on their data in an application."

  (session-details
    [store user-data]
    "Accepts a session-store and data which would identify the user whose session details will be
     retreived."))

(defprotocol ModifyUserSession
  "A protocol for modifying the session(s) of a user that is not actively controlling the application.
   For example, if an administrator wants to disable/delete a user account, they will be able to terminate
   all existing user sessions."

  (terminate-user-sessions
    [store keys reason]
    "Accepts a session-store, multiple keys (session-ids) to terminate the user session(s) and a reason for why
     the session was terminated."))
