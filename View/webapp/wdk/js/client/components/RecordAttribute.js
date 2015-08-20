import React from 'react';
import wrappable from '../utils/wrappable';
import {
  formatAttributeValue
} from '../utils/stringUtils';

let RecordAttribute = React.createClass({

  propTypes: {
    value: React.PropTypes.oneOfType([
      React.PropTypes.string,
      React.PropTypes.object
    ]).isRequired
  },

  render() {
    let { value } = this.props;
    return (
      <div
        className="wdk-Record-attributeContent"
        dangerouslySetInnerHTML={{__html: formatAttributeValue(value)}}
      />
    );
  }

});

export default wrappable(RecordAttribute);
