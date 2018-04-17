'use strict';var _createClass=function(){function defineProperties(target,props){for(var descriptor,i=0;i<props.length;i++)descriptor=props[i],descriptor.enumerable=descriptor.enumerable||!1,descriptor.configurable=!0,'value'in descriptor&&(descriptor.writable=!0),Object.defineProperty(target,descriptor.key,descriptor)}return function(Constructor,protoProps,staticProps){return protoProps&&defineProperties(Constructor.prototype,protoProps),staticProps&&defineProperties(Constructor,staticProps),Constructor}}(),_react=require('react'),_react2=_interopRequireDefault(_react),_propTypes=require('prop-types'),_propTypes2=_interopRequireDefault(_propTypes),_HeadingRow=require('../Ui/HeadingRow'),_HeadingRow2=_interopRequireDefault(_HeadingRow),_DataRowList=require('../Ui/DataRowList'),_DataRowList2=_interopRequireDefault(_DataRowList);Object.defineProperty(exports,'__esModule',{value:!0});function _interopRequireDefault(obj){return obj&&obj.__esModule?obj:{default:obj}}function _classCallCheck(instance,Constructor){if(!(instance instanceof Constructor))throw new TypeError('Cannot call a class as a function')}function _possibleConstructorReturn(self,call){if(!self)throw new ReferenceError('this hasn\'t been initialised - super() hasn\'t been called');return call&&('object'==typeof call||'function'==typeof call)?call:self}function _inherits(subClass,superClass){if('function'!=typeof superClass&&null!==superClass)throw new TypeError('Super expression must either be null or a function, not '+typeof superClass);subClass.prototype=Object.create(superClass&&superClass.prototype,{constructor:{value:subClass,enumerable:!1,writable:!0,configurable:!0}}),superClass&&(Object.setPrototypeOf?Object.setPrototypeOf(subClass,superClass):subClass.__proto__=superClass)}var DataTable=function(_React$PureComponent){function DataTable(props){return _classCallCheck(this,DataTable),_possibleConstructorReturn(this,(DataTable.__proto__||Object.getPrototypeOf(DataTable)).call(this,props))}return _inherits(DataTable,_React$PureComponent),_createClass(DataTable,[{key:'render',value:function render(){var _props=this.props,rows=_props.rows,options=_props.options,columns=_props.columns,actions=_props.actions,uiState=_props.uiState,eventHandlers=_props.eventHandlers,props={rows:rows,options:options,columns:columns,actions:actions,uiState:uiState,eventHandlers:eventHandlers};return _react2.default.createElement('div',{className:'DataTable'},_react2.default.createElement('table',{cellSpacing:'0',cellPadding:'0'},_react2.default.createElement('tbody',null,_react2.default.createElement(_HeadingRow2.default,props)),_react2.default.createElement(_DataRowList2.default,props)))}}]),DataTable}(_react2.default.PureComponent);DataTable.propTypes={rows:_propTypes2.default.array,columns:_propTypes2.default.array,options:_propTypes2.default.object,actions:_propTypes2.default.arrayOf(_propTypes2.default.shape({element:_propTypes2.default.oneOfType([_propTypes2.default.func,_propTypes2.default.node,_propTypes2.default.element]),handler:_propTypes2.default.func,callback:_propTypes2.default.func})),uiState:_propTypes2.default.object,eventHandlers:_propTypes2.default.objectOf(_propTypes2.default.func)},exports.default=DataTable;