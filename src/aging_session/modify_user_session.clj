(ns aging-session.modify-user-session)

(defprotocol ModifyUserSession
  "A protocol for modifying the session(s) of a user who is not currently logged in and
   controlling the application."

  (terminate-user-session
    [store x]
    [store x y]
    "Arity-2 or 3 methods which receive the arguments: session-store and one or two unique ids to identify the user whose
    session needs to be terminated."))
