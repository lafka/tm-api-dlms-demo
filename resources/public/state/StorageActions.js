import WebSocketActions from './ws.js'

const StorageActions = {
   remoteRefresh: function(optionalResources) {
      if (_.isArray(optionalResources))
         return WebSocketActions.request('remote-refresh', {resources: optionalResurces})
      else
         return WebSocketActions.request('remote-refresh', {})
   },

   device: function(nid, device) {
      return WebSocketActions.request('store-device', {device: nid + '/' + device})
   },

   devices: function(nid) {
      return WebSocketActions.request('store-devices', {network: nid || null})
   },

   network: function(nid) {
      return WebSocketActions.request('store-network', {network: nid || null})
   },

   networks: function() {
      return WebSocketActions.request('store-networks', {})
   },

   subscribe: function(nid) {
      return WebSocketActions.request('storage-subscribe', {network: nid || null})
   },

   unsubscribe: function(nid) {
      return WebSocketActions.request('storage-unsubscribe', {network: nid || null})
   }
}

export default StorageActions
