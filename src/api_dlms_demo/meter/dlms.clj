(ns api-dlms-demo.meter.dlms
  (:import (org.openmuc.jdlms.internal AuthenticationMechanism ConfirmedMode)
           (org.openmuc.jdlms.internal.security DataTransmissionLevel)
           (api_dlms_demo.cloud DLMSSettings)))

(def settings
  (new DLMSSettings
       AuthenticationMechanism/NONE       ;; authenticationMechanism
       (byte-array [])                    ;; authenticationKey
       ConfirmedMode/CONFIRMED            ;; confirmedMode
       (byte-array [])                    ;; globalEncryptionKey
       (byte-array [])                    ;; systemTitle
       0                                  ;; challengeLength
       0                                  ;; deviceId
       10000                              ;; responseTimeout
       0                                  ;; messageFragmentTimeout
       ""                                 ;; manufactureId
       DataTransmissionLevel/UNENCRYPTED  ;; dataTransmissionLevel
       0                                  ;; clientAccessPoint
       0                                  ;; logicalDeviceAddress
       ))