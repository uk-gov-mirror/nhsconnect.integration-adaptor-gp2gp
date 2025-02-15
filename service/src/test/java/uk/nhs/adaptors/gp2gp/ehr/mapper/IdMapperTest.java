package uk.nhs.adaptors.gp2gp.ehr.mapper;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import org.hl7.fhir.dstu3.model.IdType;
import org.hl7.fhir.dstu3.model.Reference;
import org.hl7.fhir.dstu3.model.ResourceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import uk.nhs.adaptors.gp2gp.common.service.RandomIdGeneratorService;
import uk.nhs.adaptors.gp2gp.ehr.exception.EhrMapperException;

@ExtendWith(MockitoExtension.class)
public class IdMapperTest {
    private final RandomIdGeneratorService randomIdGeneratorService = new RandomIdGeneratorService();
    private IdMapper idMapper;

    @BeforeEach
    public void setUp() {
        idMapper = new IdMapper(randomIdGeneratorService);
    }

    @Test
    public void When_FetchingSameIdTwiceForTheSameResource_Expect_SameMappedIdReturned() {
        String fhirId = randomIdGeneratorService.createNewId();

        String mappedId = idMapper.getOrNew(ResourceType.Appointment, fhirId);

        assertThat(idMapper.getOrNew(ResourceType.Appointment, fhirId)).isEqualTo(mappedId);
    }

    @Test
    public void When_FetchingTwoDifferentIdsForTheSameResource_Expect_NewMappedIdsReturned() {
        String firstFhirId = randomIdGeneratorService.createNewId();
        String secondFhirId = randomIdGeneratorService.createNewId();

        String firstMappedId = idMapper.getOrNew(ResourceType.Appointment, firstFhirId);
        String secondMappedId = idMapper.getOrNew(ResourceType.Appointment, secondFhirId);

        assertThat(firstMappedId).isNotEqualTo(secondMappedId);
    }

    @Test
    public void When_FetchingSameIdForDifferentResources_Expect_NewMappedIdsReturned() {
        String sameFhirId = randomIdGeneratorService.createNewId();

        String firstMappedId = idMapper.getOrNew(ResourceType.Appointment, sameFhirId);
        String secondMappedId = idMapper.getOrNew(ResourceType.Encounter, sameFhirId);

        assertThat(firstMappedId).isNotEqualTo(secondMappedId);
    }

    @Test
    public void When_FetchingSameIdTwiceForTheSameResourceReference_Expect_SameMappedIdReturned() {
        String fhirId = randomIdGeneratorService.createNewId();

        Reference reference = new Reference(new IdType(ResourceType.Appointment.name(), fhirId));
        String mappedId = idMapper.getOrNew(reference);

        assertThat(idMapper.getOrNew(reference)).isEqualTo(mappedId);
    }

    @Test
    public void When_FetchingTwoDifferentIdsForTheSameResourceReference_Expect_NewMappedIdsReturned() {
        String firstFhirId = randomIdGeneratorService.createNewId();
        String secondFhirId = randomIdGeneratorService.createNewId();

        Reference firstReference = new Reference(new IdType(ResourceType.Appointment.name(), firstFhirId));
        String firstMappedId = idMapper.getOrNew(firstReference);

        Reference secondReference = new Reference(new IdType(ResourceType.Appointment.name(), secondFhirId));
        String secondMappedId = idMapper.getOrNew(secondReference);

        assertThat(firstMappedId).isNotEqualTo(secondMappedId);
    }

    @Test
    public void When_FetchingSameIdForDifferentResourcesReference_Expect_NewMappedIdsReturned() {
        String sameFhirId = randomIdGeneratorService.createNewId();

        Reference firstReference = new Reference(new IdType(ResourceType.Appointment.name(), sameFhirId));
        String firstMappedId = idMapper.getOrNew(firstReference);

        Reference secondReference = new Reference(new IdType(ResourceType.Encounter.name(), sameFhirId));
        String secondMappedId = idMapper.getOrNew(secondReference);

        assertThat(firstMappedId).isNotEqualTo(secondMappedId);
    }

    @Test
    public void When_GettingExtantId_Expect_ExtantIdReturned() {
        String id = randomIdGeneratorService.createNewId();
        var reference = new Reference(new IdType(ResourceType.Person.name(), id));
        String expected = idMapper.getOrNew(reference);

        String actual = idMapper.get(reference);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void When_GettingMissingId_Expect_Exception() {
        String id = randomIdGeneratorService.createNewId();
        var reference = new Reference(new IdType(ResourceType.Person.name(), id));

        assertThatThrownBy(() -> idMapper.get(reference))
            .isExactlyInstanceOf(EhrMapperException.class)
            .hasMessage("No ID mapping for reference Person/%s", id);
    }
}
