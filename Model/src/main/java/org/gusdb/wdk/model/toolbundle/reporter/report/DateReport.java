package org.gusdb.wdk.model.toolbundle.reporter.report;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.vulpine.lib.json.schema.Schema;
import io.vulpine.lib.json.schema.SchemaBuilder;
import io.vulpine.lib.json.schema.v4.Format;

import org.gusdb.fgputil.ComparableLocalDateTime;
import org.gusdb.fgputil.SortDirection;
import org.gusdb.fgputil.functional.Result;
import org.gusdb.fgputil.runtime.JvmUtil;
import org.gusdb.wdk.model.WdkModelException;

import static org.gusdb.fgputil.FormatUtil.NL;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

@JsonPropertyOrder({
  DateReport.KEY_MIN_VAL,
  DateReport.KEY_MAX_VAL,
  AbstractReport.KEY_TOTAL,
  AbstractReport.KEY_UNIQUE,
  AbstractReport.KEY_NULLS,
  AbstractReport.KEY_VALUES
})
public class DateReport extends AbstractReport<ComparableLocalDateTime> {

  static final String
    KEY_MIN_VAL = "minValue",
    KEY_MAX_VAL = "maxValue";

  /**
   * Approximate size in bytes of a LocalDateTime object (64 on 64bit, 40 on
   * 32bit)
   */
  private static final byte DATE_SIZE = (byte) padSize(
    JvmUtil.OBJECT_HEADER_SIZE * 3 // LocalDateTime, LocalDate, LocalTime
    + Integer.BYTES * 2            // LD.year, LT.nano
    + Short.BYTES * 2              // LD.month, LD.day
    + Byte.BYTES * 3               // LT.minute, LT.hour, LT.second
  );

  private LocalDateTime max;
  private LocalDateTime min;

  public DateReport(long maxVals, SortDirection sort) {
    super(maxVals, sort);
  }

  public DateReport(SortDirection sort) {
    super(sort);
  }

  @Override
  public ComparableLocalDateTime parse(String val) throws WdkModelException {
    if (val == null)
      return null;
    return tryParse(val)
      .mapError(WdkModelException::new)
      .valueOrElseThrow();
  }

  private Result<Exception, ComparableLocalDateTime> tryParse(String val) {
    var err = new ArrayList<String>();
    err.add("Failed to parse date: " + val);

    // Default parse
    try { return Result.value(new ComparableLocalDateTime(LocalDateTime.parse(val))); }
    catch (Exception e) { err.add(e.getMessage()); }

    // Wonkydate parse
    try { return Result.value(new ComparableLocalDateTime(LocalDateTime.parse(val,
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.S")))); }
    catch (Exception e) { err.add(e.getMessage()); }

    return Result.error(new Exception(String.join(NL, err)));
  }

  @Override
  protected int sizeOf(ComparableLocalDateTime val) {
    return DATE_SIZE;
  }

  @Override
  public void pushValue(ComparableLocalDateTime val) {
    super.pushValue(val);
    var underlyingVal = val.get();
    if (max == null || underlyingVal.isAfter(max))
      max = underlyingVal;
    if (min == null || underlyingVal.isBefore(min))
      min = underlyingVal;
  }

  @JsonGetter(KEY_MAX_VAL)
  public String getMax() {
    return max.toString();
  }

  @JsonGetter(KEY_MIN_VAL)
  public String getMin() {
    return min.toString();
  }

  public static SchemaBuilder outputSchema() {
    var js = Schema.draft4();
    return AbstractReport.outputSpec()
      .requiredProperty(KEY_MAX_VAL, js.asString().format(Format.DATE_TIME)
        .description("Highest value that appears in the results"))
      .requiredProperty(KEY_MIN_VAL, js.asString().format(Format.DATE_TIME)
        .description("Lowest value that appears in the results"))
      .requiredProperty(KEY_VALUES, js.asArray()
        .items(js.asObject()
          .requiredProperty(Pair.KEY_VALUE, js.asString().format(Format.DATE_TIME))
          .requiredProperty(Pair.KEY_COUNT, js.asInteger().minimum(0)))
        .description("An array distinct values and their frequency")
        .uniqueItems(true));
  }
}
