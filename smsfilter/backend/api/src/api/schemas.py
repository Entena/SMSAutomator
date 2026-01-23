from pydantic import BaseModel, Field


class SMSFilterRequest(BaseModel):
    sms: str = Field(..., description="SMS message to filter")
    violent_crimes: bool | None = Field(None, description="Filter violent crimes")
    nonviolent_crimes: bool | None = Field(None, description="Filter nonviolent crimes")
    sex_related_crimes: bool | None = Field(
        None, description="Filter sex-related crimes"
    )
    child_sexual_exploitation: bool | None = Field(
        None, description="Filter child sexual exploitation"
    )
    defamation: bool | None = Field(None, description="Filter defamation")
    specialized_advice: bool | None = Field(
        None, description="Filter specialized advice"
    )
    privacy: bool | None = Field(None, description="Filter privacy")
    intellectual_property: bool | None = Field(
        None, description="Filter intellectual property"
    )
    indiscriminate_weapons: bool | None = Field(
        None, description="Filter indiscriminate weapons"
    )
    hate: bool | None = Field(None, description="Filter hate")
    suicide_and_self_harm: bool | None = Field(
        None, description="Filter suicide and self-harm"
    )
    sexual_content: bool | None = Field(None, description="Filter sexual content")
    elections: bool | None = Field(None, description="Filter elections")
