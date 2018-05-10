package org.gusdb.wdk.cache;

import java.util.Date;

import org.gusdb.wdk.model.answer.spec.AnswerSpec;
import org.json.JSONObject;

public class AnswerRequest {

  private final Date _creationDate;
  private final AnswerSpec _answerSpec;
  private final JSONObject _formatting;

  public AnswerRequest(AnswerSpec answerSpec, JSONObject formatting) {
    _creationDate = new Date();
    _answerSpec = answerSpec;
    _formatting = formatting;
  }

  public Date getCreationDate() {
    return _creationDate;
  }

  public AnswerSpec getAnswerSpec() {
    return _answerSpec;
  }

  public JSONObject getFormatting() {
    return _formatting;
  }
}