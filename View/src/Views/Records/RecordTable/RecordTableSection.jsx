import PropTypes from 'prop-types';
import { includes } from 'lodash';
import { safeHtml, wrappable } from 'Utils/ComponentUtils';
import RecordTable from 'Views/Records/RecordTable/RecordTable';
import CollapsibleSection from 'Components/Display/CollapsibleSection';
import ErrorBoundary from 'Core/Controllers/ErrorBoundary';

/** Record table section on record page */
function RecordTableSection(props) {
  let { table, record, recordClass, isCollapsed, onCollapsedChange } = props;
  let { name, displayName, description } = table;
  let value = record.tables[name];
  let isError = includes(record.tableErrors, name);
  let isLoading = value == null;
  let className = [ 'wdk-RecordTable', 'wdk-RecordTable__' + table.name ].join(' ');

  return (
    <CollapsibleSection
      id={name}
      className="wdk-RecordTableContainer"
      headerContent={displayName}
      isCollapsed={isCollapsed}
      onCollapsedChange={onCollapsedChange}
    >
      <ErrorBoundary>
        {description && <p>{safeHtml(description)}</p>}
        { isError ? <p style={{ color: 'darkred', fontStyle: 'italic' }}>Unable to load data due to a server error.</p>
        : isLoading ? <p>Loading...</p>
        : <RecordTable className={className} value={value} table={table} record={record} recordClass={recordClass}/> }
      </ErrorBoundary>
    </CollapsibleSection>
  );
}

RecordTableSection.propTypes = {
  table: PropTypes.object.isRequired,
  ontologyProperties: PropTypes.objectOf(PropTypes.arrayOf(PropTypes.string)).isRequired,
  record: PropTypes.object.isRequired,
  recordClass: PropTypes.object.isRequired,
  isCollapsed: PropTypes.bool.isRequired,
  onCollapsedChange: PropTypes.func.isRequired
};

export default wrappable(RecordTableSection);