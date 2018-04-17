'use strict';Object.defineProperty(exports,'__esModule',{value:!0}),exports.ActionDefaults=exports.UiStateDefaults=exports.OptionsDefaults=exports.ColumnDefaults=void 0;var _react=require('react'),_react2=_interopRequireDefault(_react),_Utils=require('./Utils/Utils'),_Utils2=_interopRequireDefault(_Utils),_Icon=require('./Components/Icon'),_Icon2=_interopRequireDefault(_Icon);function _interopRequireDefault(obj){return obj&&obj.__esModule?obj:{default:obj}}var ColumnDefaults=exports.ColumnDefaults={primary:!1,searchable:!0,sortable:!0,resizeable:!0,truncated:!1,filterable:!1,filterState:{enabled:!1,visible:!1,blacklist:[]},hideable:!0,hidden:!1,disabled:!1,type:'text'},OptionsDefaults=exports.OptionsDefaults={title:null,toolbar:!0,inline:!1,className:null,showCount:!0,errOnOverflow:!1,editableColumns:!0,overflowHeight:'16em',searchPlaceholder:'Search This Table',isRowSelected:function isRowSelected(){return!1}},UiStateDefaults=exports.UiStateDefaults={searchQuery:null,filteredRowCount:0,sort:{columnKey:null,direction:'asc'},pagination:{currentPage:1,totalPages:null,totalRows:null,rowsPerPage:20}},ActionDefaults=exports.ActionDefaults=[{element:function element(rows){var text=rows.length?_react2.default.createElement('span',null,'Export ',_react2.default.createElement('b',null,rows.length),' rows as .csv'):_react2.default.createElement('span',null,'Export all rows as .csv'),icon=_react2.default.createElement(_Icon2.default,{fa:'table'});return _react2.default.createElement('button',null,text,' ',icon)},callback:function callback(selectedRows,columns,rows){var exportable=selectedRows.length?selectedRows:rows;console.log(_Utils2.default.createCsv(exportable,columns))}}];