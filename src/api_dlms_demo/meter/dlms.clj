(ns api-dlms-demo.meter.dlms
  (:import (org.openmuc.jdlms.internal AuthenticationMechanism ConfirmedMode)
           (org.openmuc.jdlms.internal.security DataTransmissionLevel)
           (api_dlms_demo.cloud DLMSSettings)))

(def enc-key (map byte "IAMENCRYPTIONKEY"))
(def settings
  (new DLMSSettings
       AuthenticationMechanism/LOW        ;; authenticationMechanism
       (byte-array (map byte "ABCDEFGH")) ;; authenticationKey
       ConfirmedMode/CONFIRMED            ;; confirmedMode
       (byte-array enc-key)               ;; globalEncryptionKey
       (byte-array (map byte "tinymesh")) ;; systemTitle
       0                                  ;; challengeLength
       0                                  ;; deviceId
       10000                              ;; responseTimeout
       10000                              ;; messageFragmentTimeout
       ""                                 ;; manufactureId
       DataTransmissionLevel/UNENCRYPTED  ;; dataTransmissionLevel
       0                                  ;; clientAccessPoint
       0                                  ;; logicalDeviceAddress
       ))
