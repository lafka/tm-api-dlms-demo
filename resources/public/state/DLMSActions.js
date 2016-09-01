import WebSocketActions from './ws.js'

export const DLMSActions = {
   read: function(resource, def, attr) {
      return WebSocketActions.request('dlms-queue', {action: ["read", resource, attr]})
   },

   write: function(resource, def, attr, val) {
      return WebSocketActions.request('dlms-queue', {action: ["write", resource, attr, [def[3], val]]})
   },

   exec: function(resource, def, attr) {
      return WebSocketActions.request('dlms-queue', {action: ["exec", resource, attr]})
   },

   cancel: function(resource, def, attr, val) {
      if (def[3] && def[3] === 'exec')
         return WebSocketActions.request('dlms-queue', {action: ["cancel", ["exec", resource, attr]]})
      else if (def[3])
         return WebSocketActions.request('dlms-queue', {action: ["cancel", ["write", resource, attr, [def[3], val]]]})
      else
         return WebSocketActions.request('dlms-queue', {action: ["cancel", ["read", resource, attr]]})
   },

   cancelConcrete(job) {
      return WebSocketActions.request('dlms-queue', {action: ["cancel", job]})
   },

   queue: function(nid) {
      if (!nid)
         throw "dlms-queue-state: undefined network"

      return WebSocketActions.request('dlms-queue-state', {network: nid})
   },

   attributes: function(resource) {
      return WebSocketActions.request('dlms-attributes', {resource: resource})
   }
}

//
//
//
//
//DLMSActions.read "18", "19" ["1/255.255/3"]
//-> sends a dlms-attrs read request
//-> receives new worker queue item (grouped by (nid,dev,attr))
//-> when work done `DLMS.attr "18", "19", "1/255.255/3"` -> attr
