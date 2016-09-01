import React from 'react'

import {Button, FormControl} from 'react-bootstrap'

import {DLMSActions} from '../state/DLMSActions.js'
import DLMS from '../state/DLMS.js'

const byteToHex = (c, prefix) => (true === prefix ? "\\x" : " ")  + ("0" + c.toString(16)).slice(-2)

const maybeFmt = function(k, v, fmt) {
   if (fmt && _.isFunction(fmt))
      return fmt(v)

   if (_.isArray(v))
      return _.reduce(v, (acc, val) => acc + (val < 32 || val > 126 ? byteToHex(val, true)
                                                                             : String.fromCharCode(val)), "")
   else if (_.isNumber(v))
      return v.toString()

   else if (_.isBoolean(v))
      return v.toString()

   return v
}

const fmtOrDummy = function(attr, value, definition) {
   if (_.isObject(value) && undefined !== value.value) {
      return maybeFmt(attr, value.value, (definition || {})[2])
   } else
      return <span style={{width: 101 + "px"}} className="dummy-block">&nbsp;</span>
}

const rawOrDummy = function(resource, attr) {
   let val = (DLMS.value(resource, attr) || [])

   if (val) {
      if (val.raw)
         return <span>{_.map(val.raw, byteToHex)}</span>
      if (val.error)
         return <span>{val.error}</span>
      else
         return <span className="dummy-block">no raw data received</span>
   }
   else
      return <span style={{width: 197  + "px"}} className="dummy-block">&nbsp;</span>
}
export default class AttributeList extends React.Component {
   constructor() {
      super()

      this.queueRead  = this.queueRead.bind(this)
      this.queueWrite = this.queueWrite.bind(this)
      this.queueExec  = this.queueExec.bind(this)

      this.state = {
         updates: {}
      }

      this._mounted = false
   }

   componentWillMount() {
      this._mounted = true

      let resource = this.props.resource
      DLMSActions.attributes(resource)

      DLMS.addChangeListener(this._dlmsListener = () => {
         this._mounted && this.forceUpdate()
      })
   }

   componentWillReceiveProps(nextProps) {
      let resource = this.props.resource
      DLMSActions.attributes(resource)
   }


   componentWillUnmount() {
      this._mounted = false
      DLMS.removeChangeListener(this._dlmsListener)

   }

   queueRead(attr, def) {
      let resource = this.props.resource
      DLMSActions.read(resource, def, def[5] || attr)
   }

   queueWrite(attr, def, value) {
      let resource = this.props.resource
      DLMSActions.write(resource, def, attr, value)
   }

   queueExec(attr, def) {
      let resource = this.props.resource
      DLMSActions.exec(resource, def, attr)
   }

   cancelOperation(job) {
      DLMSActions.cancelConcrete(job)
   }

   render() {

      console.log('render: attribute list')

      let {attrs, definition, resource, queue} = this.props,
          {updates} = this.state,
          queuemap = _.reduce(queue, (acc, v, k) => {
            let [op, _resource, attr] = v
            acc[op + '#' + attr] = [k, v]
            return acc
          }, {}),
          head = queue[0]

      return <div className="attr-list">
         <table className="table table-striped">
            <thead>
               <tr>
                  <th>Name</th>
                  <th>LN</th>
                  <th>Value</th>
                  <th>Raw/Input</th>
                  <th>#</th>
               </tr>
            </thead>

            <tbody>
               {_.map(attrs, (attr, k) => {
                  let job = (op, attr) => (queuemap[op + '#' + attr] || [])[1],
                      current = head && head[2] === attr,
                      className = []

                  if (_.some(queue, (work) => work[2] === attr)) // in queue
                     className = _.concat(className, ['loading'])
                  if (head && head[2] === attr) // first in line
                     className = _.concat(className, ['reading'])

                  className = className.join(' ')

                  return <tr key={k} className={className}>
                     <td>{(definition[attr] || [])[1] || attr}</td>
                     <td>{attr}</td>
                     <td>{fmtOrDummy(attr, DLMS.value(resource, definition[attr][5] || attr), definition[attr])}</td>
                     <td><RawOrInput resource={resource}
                                     definition={definition[attr]}
                                     value={updates[attr] || DLMS.value(resource, definition[attr][5] || attr)}
                                     onChange={ev => {let val = ev.target.value; this.setState(old => _.set(old, ['updates', attr], val))}}
                                     attr={attr} /></td>
                     <td>
                        <Button onClick={() => this.queueRead(attr, definition[attr])}>Request Data</Button>

                        {definition[attr][3] && definition[attr][3] === 'exec' &&
                           <Button onClick={() => this.queueExec(attr, definition[attr])}>Execute</Button>}

                        {definition[attr][3] && definition[attr][3] !== 'exec' &&
                           <Button onClick={() => this.queueWrite(attr, definition[attr], updates[attr])}>Update</Button>}

                        {job('read', attr)  && <Button onClick={() => this.cancelOperation(job('read', attr))} >Cancel read</Button>}
                        {job('write', attr) && <Button onClick={() => this.cancelOperation(job('write', attr))}>Cancel write</Button>}
                        {job('exec', attr)  && <Button onClick={() => this.cancelOperation(job('exec', attr))} >Cancel exec</Button>}
                     </td>
                  </tr>
               })}
            </tbody>
         </table>
      </div>
   }
}

class RawOrInput extends React.Component {
   render() {
      let {resource, attr, value, definition, onChange} = this.props

      if (definition[3])
         return <Value
            resource={resource}
            type={definition[3]}
            code={attr}
            value={value}
            onChange={onChange} />
      else
         return rawOrDummy(resource, attr)
   }
}

class Value extends React.Component {
   render() {
      let
         {type, value, onChange, code} = this.props,
         resource = this.props.resource

      if (!value)
         value = DLMS.value(resource, code)

      if (_.isArray((value || {}).value))
         value = _.reduce(value.value, (acc, val) => acc + (val < 32 || val > 126 ? byteToHex(val) : String.fromCharCode(val)), "")

      if (_.isObject(value) && value.value)
         value = value.value
      else if (_.isObject(value) && value.error)
         value = "Error: " + value.error


      switch (type) {
         case 'int':
            return <FormControl
                     onChange={onChange}
                     value={value || ""}
                     placeholder="int" />

         case 'float':
            return <FormControl
                     onChange={onChange}
                     value={value || ""}
                     placeholder="float" />

         case 'bit':
            return <FormControl
                        onChange={onChange}
                        value={value || ""}
                        componentClass="select"
                        placeholder="...">

                     <option value="0">0</option>
                     <option value="1">1</option>
                   </FormControl>

         case 'bool':
            return <FormControl
                        onChange={onChange}
                        componentClass="select"
                        value={value || ""}
                        placeholder="...">

                     <option value="true">true</option>
                     <option value="false">false</option>
                   </FormControl>

         case 'string':
         case 'exec':
            return <span>{value}</span>
      }
   }
}

