import debounce from 'lodash/function/debounce';
import React from 'react';
import ReactDOM from 'react-dom';
import AnswerFilterSelector from './AnswerFilterSelector';
import Tooltip from './Tooltip';
import { wrappable } from '../utils/componentUtils';

// concatenate each item in items with arr
function addToArray(arr, item) {
  return arr.concat(item);
}

function removeFromArray(arr, item) {
  return arr.filter(function(a) {
    return a !== item;
  });
}

let AnswerFilter = React.createClass({

  getInitialState() {
    let { filterAttributes, filterTables } = this.props;
    return {
      showFilterFieldSelector: false,
      filterAttributes,
      filterTables
    };
  },

  componentWillMount() {
    this.handleFilter = debounce(this.handleFilter, 100);
  },

  componentDidUpdate(prevProps, prevState) {
    let { filterAttributes, filterTables } = this.state;
    if (filterAttributes !== prevState.filterAttributes || filterTables !== prevState.filterTables) {
      this.handleFilter();
    }
  },

  toggleFilterFieldSelector() {
    this.setState({ showFilterFieldSelector: !this.state.showFilterFieldSelector });
  },

  handleFilter() {
    let value = ReactDOM.findDOMNode(this.refs.filterInput).value;
    let { filterAttributes, filterTables } = this.state;
    this.props.answerEvents.onFilter(value, filterAttributes, filterTables);
  },

  toggleAttribute(e) {
    let attr = e.target.value;
    let op = e.target.checked ? addToArray : removeFromArray;
    this.setState({
      filterAttributes: op(this.state.filterAttributes, attr)
    });
  },

  toggleTable(e) {
    let table = e.target.value;
    let op = e.target.checked ? addToArray : removeFromArray;
    this.setState({
      filterTables: op(this.state.filterTables, table)
    });
  },

  selectAll(e) {
    let { attributes, tables } = this.props.recordClass;
    this.setState({
      filterAttributes: attributes.map(a => a.name),
      filterTables: tables.map(t => t.name)
    });
    e.preventDefault();
  },

  clearAll(e) {
    this.setState({ filterAttributes: [], filterTables: [] });
    e.preventDefault();
  },

  render() {
    let { filterAttributes, filterTables, showFilterFieldSelector } = this.state;
    let { recordClass, filterTerm } = this.props;
    let { displayNamePlural } = recordClass;
    /*
    let tooltipContent = (
      <div>
        <p>
          Enter words or phrases that you wish to query. Words should be
          separated by spaces, and phrases should be enclosed in double-quotes.
        </p>
        <p>
          {displayNamePlural} displayed will contain these words or phrases
          in any field. All words and phrases are partially matched.
        </p>
        <p>
          For example, the word <i>typical</i> will match both the
          word <i><u>typical</u>ly</i> and the word <i>a<u>typical</u></i>.
        </p>
      </div>
    );
    */
    let tooltipContent = (
      <div>
        <ul>
        <li>The data sets in your refined list will contain ALL your terms (or phrases, when using double quotes), in ANY of the selected fields.</li>
        <li>Click on the arrow inside the box to select/unselect fields. </li>
        <li>Your terms are partially matched; 
            for example, the term <i>typ</i> will match <i><u>typ</u>ically</i>, <i><u>typ</u>e</i>, <i>a<u>typ</u>ical</i>.</li>
        </ul>
      </div>
    );

    return (
      <div className="wdk-Answer-filter">
        <input
          ref="filterInput"
          className="wdk-Answer-filterInput"
          defaultValue={filterTerm}
          placeholder={`Search ${displayNamePlural}`}
          onChange={this.handleFilter}
        />
        <Tooltip content="Show search fields">
          <button className="fa fa-caret-down wdk-Answer-filterSelectFieldsIcon"
            onClick={this.toggleFilterFieldSelector}/>
        </Tooltip>
        <Tooltip content={tooltipContent}>
          <i className="fa fa-question-circle fa-lg wdk-Answer-filterInfoIcon"/>
        </Tooltip>

        <AnswerFilterSelector
          recordClass={recordClass}
          open={showFilterFieldSelector}
          onClose={this.toggleFilterFieldSelector}
          filterAttributes={filterAttributes}
          filterTables={filterTables}
          selectAll={this.selectAll}
          clearAll={this.clearAll}
          toggleAttribute={this.toggleAttribute}
          toggleTable={this.toggleTable}
        />

      </div>
    );
  }

});

export default wrappable(AnswerFilter);